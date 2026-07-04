# StrangerCam (Zego + Spring Boot)
A minimal Omegle-style "random stranger video chat" app. Spring Boot backend
does the two jobs that matter: (1) generates ZegoCloud room tokens, and
(2) matches two waiting users into a random room. Frontend is intentionally
plain HTML/JS just to exercise the flow.
- No database — matches live in memory and reset on restart.
- No user accounts/auth — userID is just name + random number.

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
