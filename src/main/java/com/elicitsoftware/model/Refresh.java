package com.elicitsoftware.model;

public class Refresh {
    public String xid;
    public long respondentId;
    public String messageStatus;

    public Refresh(String xid, long respondentId, String messageStatus) {
        this.xid = xid;
        this.respondentId = respondentId;
        this.messageStatus = messageStatus;
    }
}
