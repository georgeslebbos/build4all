package com.build4all.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CancelRequestDTO {

    @NotBlank(message = "reason is required")
    @Size(max = 500, message = "reason must be <= 500 chars")
    private String reason;

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
