package com.example.zegoapp.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.zegoapp.service.ZegoTokenService;

@RestController
public class TokenController {

    private final ZegoTokenService zegoTokenService;

    public TokenController(ZegoTokenService zegoTokenService) {
        this.zegoTokenService = zegoTokenService;
    }

    @GetMapping("/api/token")
    public Map<String, Object> getToken(@RequestParam String userID, @RequestParam String roomID) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (userID == null || userID.isBlank() || roomID == null || roomID.isBlank()) {
            result.put("error", "userID and roomID are required");
            return result;
        }
        if (!zegoTokenService.isConfigured()) {
            result.put("error", "Server is missing ZEGO_APP_ID / ZEGO_SERVER_SECRET. Set them as env vars.");
            return result;
        }

        try {
            long effectiveTimeInSeconds = 3600;
            String token = zegoTokenService.generateToken(userID, effectiveTimeInSeconds);

            result.put("appID", zegoTokenService.getAppId());
            result.put("token", token);
            result.put("userID", userID);
            result.put("roomID", roomID);
        } catch (Exception e) {
            result.put("error", "Failed to generate token: " + e.getMessage());
        }
        return result;
    }
}
