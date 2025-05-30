package com.elicitsoftware.model;

import java.util.List;

public class ListResponse {

    private List<Subject> respondents = null;
    private long length;

    public ListResponse(List<Subject> respondents, long length) {
        this.respondents = respondents;
        this.length = length;
    }

    public List<Subject> getRespondents() {
        return respondents;
    }

    public void setRespondents(List<Subject> respondents) {
        this.respondents = respondents;
    }

    public long getLength() {
        return length;
    }

    public void setLength(long length) {
        this.length = length;
    }
}
