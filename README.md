# StrangerCam (Zego + Spring Boot)

A minimal Omegle-style "random stranger video chat" app. Spring Boot backend
does the two jobs that matter: (1) generates ZegoCloud room tokens, and
(2) matches two waiting users into a random room. Frontend is intentionally
plain HTML/JS just to exercise the flow.

## Why "Start" did nothing (the bug that was here before)

`script.js` had `import { ZegoExpressEngine } from "https://cdn.jsdelivr.net/npm/zego-express-engine-webrtc/index.js"` at the top. That package doesn't actually contain an `index.js`, and its real entry file is an old UMD/CommonJS bundle, not a proper ES module — so the browser can't `import {}` from it directly. Since it's a static `import` at the top of the file, the failure kills the *entire* script silently — no event listeners ever get attached, so clicking "Start" (or anything else) does nothing, with no visible error.

Fixed by importing through `https://esm.sh/zego-express-engine-webrtc@3.12.0` instead, which re-bundles CJS/UMD npm packages into real working ESM on the fly, and by wrapping the import in try/catch so any future load failure shows a message on screen instead of failing silently. If you ever see "Start" doing nothing again, open the browser DevTools console first — that's the fastest way to see what actually broke.

## What was fixed / added vs. the original zip

- **Bug fix**: the original code did `envLong("738726452", 0L)` — that passes
  the App ID *value* as the *name* of an env var to look up, so it always
  resolved to 0 and the token endpoint never actually worked. Config is now
  read the normal Spring way via `application.properties`.
- **Added real matchmaking**: the original app only let two people join the
  *same* room manually (you had to share a room ID/link). This version adds
  an in-memory queue (`MatchmakingService`) that randomly pairs whoever is
  waiting, generates a room ID for them, and exposes a poll-based API so the
  frontend can find out when it's matched — that's the actual "Omegle" part.
- **"Next" (skip stranger)** support: leaving a match notifies the partner so
  their client can show "stranger disconnected" and search again.
- Split the original one-giant-controller file into `controller/`, `service/`,
  `model/` packages.

## Project structure

```
zego-omegle/
├── pom.xml
├── src/main/java/com/example/zegoapp/
│   ├── ZegoAppApplication.java       # entry point
│   ├── controller/
│   │   ├── TokenController.java      # GET /api/token
│   │   └── MatchController.java      # POST /api/queue/join, GET /api/queue/status, POST /api/queue/leave
│   ├── service/
│   │   ├── ZegoTokenService.java     # Token04 generation (AES-CBC)
│   │   └── MatchmakingService.java   # in-memory random pairing queue
│   └── model/
│       └── MatchInfo.java
└── src/main/resources/
    ├── application.properties
    └── static/                       # index.html, script.js, style.css
```

## How the matching works

1. Client calls `POST /api/queue/join?userID=X&name=Y`.
2. If another user is already waiting, they're immediately paired: a random
   `roomID` is generated and both sides get it back (the caller synchronously,
   the other side on their next poll).
3. If nobody's waiting, the caller is queued and the frontend polls
   `GET /api/queue/status?userID=X` every ~1.5s until matched.
4. Once matched, both sides call the existing `GET /api/token` endpoint with
   that `roomID` to get a ZegoCloud token and join the video room via the SDK.
5. `POST /api/queue/leave?userID=X` is used for both "Leave" and "Next" — it
   removes the user and flags their partner so the partner's poll picks up
   `partnerLeft` and can search for someone new.

This is in-memory and single-instance by design (keeps it simple). If you
ever scale to multiple server instances, swap the queue/matches maps for
something shared like Redis.

## Running it

1. Get your App ID + Server Secret from the [ZegoCloud console](https://console.zegocloud.com).
2. Set env vars (recommended — don't hardcode secrets):
   ```bash
   export ZEGO_APP_ID=your_app_id
   export ZEGO_SERVER_SECRET=your_server_secret
   ```
   (`application.properties` also has fallback defaults for quick local testing
   using the credentials that were in your original zip — replace/rotate
   those before deploying anywhere public.)
3. Build & run:
   ```bash
   mvn spring-boot:run
   ```
4. Open `http://localhost:3000` in two different browser tabs/windows (or two
   devices) and click "Start" in both — they should get paired.

## Notes / limitations (kept simple on purpose)

- No database — matches live in memory and reset on restart.
- No user accounts/auth — userID is just name + random number.
- No text chat, reporting, or moderation (all things a real Omegle clone
  would need before going public).
- CORS isn't configured since frontend and backend are served from the same
  origin; add it if you split them apart.
