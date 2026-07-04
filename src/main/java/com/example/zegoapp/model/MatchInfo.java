package com.example.zegoapp.model;

/** Holds info about the stranger a user has been paired with. */
public class MatchInfo {
    public final String roomID;
    public final String partnerID;
    public final String partnerName;

    public MatchInfo(String roomID, String partnerID, String partnerName) {
        this.roomID = roomID;
        this.partnerID = partnerID;
        this.partnerName = partnerName;
    }
}
