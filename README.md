# StrangerCam (Zego + Spring Boot)
A minimal Omegle-style "random stranger video chat" app. Spring Boot backend
does the two jobs that matter: (1) generates ZegoCloud room tokens, and
(2) matches two waiting users into a random room. Frontend is intentionally
plain HTML/JS just to exercise the flow.
- No database — matches live in memory and reset on restart.
- No user accounts/auth — userID is just name + random number.

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
4. Open `http://localhost:8080` in two different browser tabs/windows (or two
   devices) and click "Start" in both — they should get paired.
