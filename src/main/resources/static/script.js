
const ZEGO_SDK_URL = "https://esm.sh/zego-express-engine-webrtc@3.12.0";

const ZEGO_SERVER = "wss://webliveroom-api.zegocloud.com/ws";
const POLL_INTERVAL_MS = 1500;

let ZegoExpressEngine;
try {
  const mod = await import(ZEGO_SDK_URL);
  
  const candidates = [mod.ZegoExpressEngine, mod.default?.ZegoExpressEngine, mod.default];
  ZegoExpressEngine = candidates.find((c) => typeof c === "function");
  if (!ZegoExpressEngine) {
    console.error("Zego SDK module shape was:", mod);
    throw new Error("Could not find the ZegoExpressEngine constructor in the loaded SDK module.");
  }
} catch (err) {
  console.error("Failed to load ZegoCloud SDK:", err);
  document.getElementById("join-error").textContent =
    "Couldn't load the video SDK (network/ad-blocker issue?). Check the browser console for details.";
}


const joinScreen = document.getElementById("join-screen");
const waitingScreen = document.getElementById("waiting-screen");
const callScreen = document.getElementById("call-screen");


const nameInput = document.getElementById("name-input");
const startBtn = document.getElementById("start-btn");
const joinError = document.getElementById("join-error");

const cancelBtn = document.getElementById("cancel-btn");

const partnerLabel = document.getElementById("partner-label");
const videoGrid = document.getElementById("video-grid");
const callStatus = document.getElementById("call-status");
const nextBtn = document.getElementById("next-btn");
const micBtn = document.getElementById("mic-btn");
const camBtn = document.getElementById("cam-btn");
const leaveBtn = document.getElementById("leave-btn");

const chatBox = document.getElementById("chat-box");
const chatForm = document.getElementById("chat-form");
const chatInput = document.getElementById("chat-input");

let zg = null;
let localStream = null;
let localStreamID = "";
let userID = "";
let userName = "";
let roomID = "";
let micOn = true;
let camOn = true;
let pollTimer = null;

startBtn.addEventListener("click", startSearch);
cancelBtn.addEventListener("click", cancelSearch);
nextBtn.addEventListener("click", nextStranger);
leaveBtn.addEventListener("click", () => endCall(true));
micBtn.addEventListener("click", toggleMic);
camBtn.addEventListener("click", toggleCam);


chatForm.addEventListener("submit", (e) => {
  e.preventDefault();
  const text = chatInput.value.trim();
  if (!text || !zg || !roomID) return;
  zg.sendBroadcastMessage(roomID, text);
  appendChatMessage("You", text);
  chatInput.value = "";
});

function appendChatMessage(sender, text) {
  const p = document.createElement("p");
  p.textContent = `${sender}: ${text}`;
  chatBox.appendChild(p);
  chatBox.scrollTop = chatBox.scrollHeight;
}

function showScreen(screen) {
  [joinScreen, waitingScreen, callScreen].forEach((s) => s.classList.add("hidden"));
  screen.classList.remove("hidden");
}

async function startSearch() {
  const name = nameInput.value.trim();
  if (!name) {
    joinError.textContent = "Please enter your name.";
    return;
  }
  joinError.textContent = "";
  userName = name;
  userID = `${name}-${Math.floor(Math.random() * 100000)}`;

  showScreen(waitingScreen);
  await enterQueue();
}

async function enterQueue() {
  try {
    const res = await fetch(
      `/api/queue/join?userID=${encodeURIComponent(userID)}&name=${encodeURIComponent(userName)}`,
      { method: "POST" }
    );
    const data = await res.json();

    if (data.status === "matched") {
      await connectToStranger(data);
    } else {
      pollForMatch();
    }
  } catch (err) {
    console.error(err);
    joinError.textContent = "Could not reach the server.";
    showScreen(joinScreen);
  }
}

function pollForMatch() {
  clearInterval(pollTimer);
  pollTimer = setInterval(async () => {
    try {
      const res = await fetch(`/api/queue/status?userID=${encodeURIComponent(userID)}`);
      const data = await res.json();

      if (data.status === "matched") {
        clearInterval(pollTimer);
        await connectToStranger(data);
      } else if (data.status === "partnerLeft") {
        // shouldn't normally happen while waiting, but handle gracefully
        clearInterval(pollTimer);
      }
    } catch (err) {
      console.error("Poll error:", err);
    }
  }, POLL_INTERVAL_MS);
}

function attachStream(video, mediaStream) {
  if (mediaStream instanceof MediaStream) {
    video.srcObject = mediaStream;
  } else if (typeof mediaStream.getMediaStream === "function") {
    video.srcObject = mediaStream.getMediaStream();
  } else if (mediaStream.stream instanceof MediaStream) {
    video.srcObject = mediaStream.stream;
  } else if (typeof mediaStream.playVideo === "function") {
    mediaStream.playVideo(video);
  } else {
    console.error("Unknown stream shape:", mediaStream, Object.getOwnPropertyNames(Object.getPrototypeOf(mediaStream)));
  }
}
async function cancelSearch() {
  clearInterval(pollTimer);
  await fetch(`/api/queue/leave?userID=${encodeURIComponent(userID)}`, { method: "POST" });
  showScreen(joinScreen);
}

async function connectToStranger(matchData) {
  roomID = matchData.roomID;

  try {
    const res = await fetch(
      `/api/token?userID=${encodeURIComponent(userID)}&roomID=${encodeURIComponent(roomID)}`
    );
    const tokenData = await res.json();
    if (!res.ok || tokenData.error) throw new Error(tokenData.error || "Failed to fetch token");

    zg = new ZegoExpressEngine(tokenData.appID, ZEGO_SERVER);
    setupZegoListeners();

    await zg.loginRoom(roomID, tokenData.token, { userID, userName });

    localStream = await zg.createZegoStream();
    localStreamID = `${userID}-stream`;
    addVideoTile(localStreamID, localStream, `${userName} (you)`, true);
    zg.startPublishingStream(localStreamID, localStream);

    partnerLabel.textContent = `Connected with: ${matchData.partnerName}`;
    callStatus.classList.add("hidden");
    showScreen(callScreen);

    pollForPartnerLeaving();
  } catch (err) {
    console.error(err);
    joinError.textContent = err.message || "Could not start the call.";
    showScreen(joinScreen);
  }
}

function pollForPartnerLeaving() {
  clearInterval(pollTimer);
  pollTimer = setInterval(async () => {
    try {
      const res = await fetch(`/api/queue/status?userID=${encodeURIComponent(userID)}`);
      const data = await res.json();
      if (data.status === "partnerLeft") {
        clearInterval(pollTimer);
        callStatus.textContent = "Stranger disconnected. Click Next to find someone new.";
        callStatus.classList.remove("hidden");
        await teardownZego();
      }
    } catch (err) {
      console.error("Poll error:", err);
    }
  }, POLL_INTERVAL_MS);
}

function setupZegoListeners() {
  zg.on("roomStreamUpdate", async (_roomID, updateType, streamList) => {
    if (updateType === "ADD") {
      for (const stream of streamList) {
        const remoteStream = await zg.startPlayingStream(stream.streamID);
        addVideoTile(stream.streamID, remoteStream, stream.user?.userName || "Stranger", false);
      }
    } else if (updateType === "DELETE") {
      for (const stream of streamList) {
        removeVideoTile(stream.streamID);
      }
    }
  });
  zg.on("IMRecvBroadcastMessage", (_roomID, chatData) => {
  chatData.forEach((msg) => appendChatMessage(msg.fromUser?.userName || "Stranger", msg.message));
});
}

function addVideoTile(streamID, mediaStream, label, isLocal) {
  removeVideoTile(streamID);
  const tile = document.createElement("div");
  tile.className = "video-tile";
  tile.id = `tile-${streamID}`;

  const video = document.createElement("video");
  video.autoplay = true;
  video.playsInline = true;
  video.muted = isLocal;
  //video.srcObject = mediaStream.getMediaStream();
  attachStream(video, mediaStream);

  const labelEl = document.createElement("div");
  labelEl.className = "label";
  labelEl.textContent = label;

  tile.appendChild(video);
  tile.appendChild(labelEl);
  videoGrid.appendChild(tile);
}

function removeVideoTile(streamID) {
  const tile = document.getElementById(`tile-${streamID}`);
  if (tile) tile.remove();
}

function toggleMic() {
  if (!localStream) return;
  micOn = !micOn;
  localStream.getAudioTracks().forEach((t) => (t.enabled = micOn));
  micBtn.classList.toggle("off", !micOn);
}

function toggleCam() {
  if (!localStream) return;
  camOn = !camOn;
  localStream.getVideoTracks().forEach((t) => (t.enabled = camOn));
  camBtn.classList.toggle("off", !camOn);
}

async function teardownZego() {
  chatBox.innerHTML = "";
  try {
    if (zg && localStreamID) zg.stopPublishingStream(localStreamID);
    if (zg && localStream) zg.destroyStream(localStream);
    if (zg && roomID) zg.logoutRoom(roomID);
  } catch (err) {
    console.warn("Error while leaving room:", err);
  }
  videoGrid.innerHTML = "";
  zg = null;
  localStream = null;
  localStreamID = "";
  roomID = "";
}

async function nextStranger() {
  chatBox.innerHTML = "";
  clearInterval(pollTimer);
  await fetch(`/api/queue/leave?userID=${encodeURIComponent(userID)}`, { method: "POST" });
  await teardownZego();
  callStatus.classList.add("hidden");
  showScreen(waitingScreen);
  await enterQueue();
}

async function endCall(returnToStart) {
  clearInterval(pollTimer);
  await fetch(`/api/queue/leave?userID=${encodeURIComponent(userID)}`, { method: "POST" });
  await teardownZego();
  if (returnToStart) {
    callStatus.classList.add("hidden");
    showScreen(joinScreen);
  }
}