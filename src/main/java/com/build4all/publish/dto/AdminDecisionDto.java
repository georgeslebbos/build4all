package com.build4all.publish.dto;

import jakarta.validation.constraints.NotNull;

public class AdminDecisionDto {

    @NotNull
    private Long requestId;

    private String notes;

    public Long getRequestId() { return requestId; }
    public void setRequestId(Long requestId) { this.requestId = requestId; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
