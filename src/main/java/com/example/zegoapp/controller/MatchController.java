package com.example.zegoapp.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.zegoapp.model.MatchInfo;
import com.example.zegoapp.service.MatchmakingService;

@RestController
public class MatchController {

    private final MatchmakingService matchmakingService;

    public MatchController(MatchmakingService matchmakingService) {
        this.matchmakingService = matchmakingService;
    }

    /** Enter the queue. Returns matched info right away if a stranger was already waiting. */
    @PostMapping("/api/queue/join")
    public Map<String, Object> join(@RequestParam String userID, @RequestParam String name) {
        MatchInfo match = matchmakingService.join(userID, name);
        return toResponse(match, false);
    }

    /** Client polls this every ~1.5s while waiting or while in a call (to detect the stranger leaving). */
    @GetMapping("/api/queue/status")
    public Map<String, Object> status(@RequestParam String userID) {
        if (matchmakingService.consumePartnerLeftFlag(userID)) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "partnerLeft");
            return result;
        }
        MatchInfo match = matchmakingService.checkMatch(userID);
        if (match != null) {
            return toResponse(match, false);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", matchmakingService.isWaiting(userID) ? "waiting" : "idle");
        return result;
    }

    /** Leave the queue or an active match (used for both "Leave" and "Next"). */
    @PostMapping("/api/queue/leave")
    public Map<String, Object> leave(@RequestParam String userID) {
        matchmakingService.leave(userID);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "left");
        return result;
    }

    private Map<String, Object> toResponse(MatchInfo match, boolean waiting) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (match != null) {
            result.put("status", "matched");
            result.put("roomID", match.roomID);
            result.put("partnerID", match.partnerID);
            result.put("partnerName", match.partnerName);
        } else {
            result.put("status", "waiting");
        }
        return result;
    }
}
