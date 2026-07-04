package com.example.zegoapp.service;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.springframework.stereotype.Service;

import com.example.zegoapp.model.MatchInfo;

/**
 * Very simple in-memory "random stranger" matchmaking queue, the core piece
 * that makes this behave like Omegle instead of a plain video-call room app.
 *
 * How it works:
 *  - A user calls join(userID, name). If someone else is already waiting,
 *    the two are immediately paired into a new random roomID.
 *  - If nobody is waiting, the user is placed in the queue until someone
 *    else joins.
 *  - Either side can poll status(userID) to find out if/when they've been
 *    matched.
 *  - leave(userID) removes the user from the queue or, if they were in an
 *    active match, notifies the partner (via the partnerLeft set) so the
 *    partner's UI can show "stranger disconnected" and search again.
 *
 * This is intentionally kept in-memory (a real production version handling
 * multiple server instances would back this with Redis or similar), which
 * matches the "keep it simple" brief.
 */
@Service
public class MatchmakingService {

    private record WaitingUser(String userID, String name) {}

    private final ConcurrentLinkedDeque<WaitingUser> waitingQueue = new ConcurrentLinkedDeque<>();
    private final Map<String, MatchInfo> matches = new ConcurrentHashMap<>();
    private final Set<String> partnerLeftFlags = ConcurrentHashMap.newKeySet();
    private final Object lock = new Object();

    /** Try to find a partner immediately, otherwise queue up. */
    public MatchInfo join(String userID, String name) {
        synchronized (lock) {
            // clear any stale state for this user first
            waitingQueue.removeIf(w -> w.userID().equals(userID));
            matches.remove(userID);
            partnerLeftFlags.remove(userID);

            WaitingUser partner = null;
            while (!waitingQueue.isEmpty()) {
                WaitingUser candidate = waitingQueue.pollFirst();
                if (candidate != null && !candidate.userID().equals(userID)) {
                    partner = candidate;
                    break;
                }
            }

            if (partner != null) {
                String roomID = "room-" + UUID.randomUUID().toString().substring(0, 8);
                MatchInfo forMe = new MatchInfo(roomID, partner.userID(), partner.name());
                MatchInfo forPartner = new MatchInfo(roomID, userID, name);
                matches.put(userID, forMe);
                matches.put(partner.userID(), forPartner);
                return forMe;
            } else {
                waitingQueue.addLast(new WaitingUser(userID, name));
                return null; // means "waiting"
            }
        }
    }

    /** Poll result: matched -> MatchInfo, still waiting -> null, partner left -> throws via flag check on caller side. */
    public MatchInfo checkMatch(String userID) {
        return matches.get(userID);
    }

    public boolean consumePartnerLeftFlag(String userID) {
        return partnerLeftFlags.remove(userID);
    }

    public boolean isWaiting(String userID) {
        return waitingQueue.stream().anyMatch(w -> w.userID().equals(userID));
    }

    /** Remove user from queue/match and let their partner know, if any. */
    public void leave(String userID) {
        synchronized (lock) {
            waitingQueue.removeIf(w -> w.userID().equals(userID));
            MatchInfo mine = matches.remove(userID);
            if (mine != null) {
                MatchInfo partnerMatch = matches.remove(mine.partnerID);
                if (partnerMatch != null) {
                    partnerLeftFlags.add(mine.partnerID);
                }
            }
        }
    }
}
