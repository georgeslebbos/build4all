package com.build4all.dto;

import jakarta.validation.constraints.Size;

/** Payload for: PUT /booking/cancel/approve|reject/... */
public class DecisionDTO {
    @Size(max = 500, message = "Note must be at most 500 characters")
    private String note;

    public DecisionDTO() {}                      // needed by Jackson

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
