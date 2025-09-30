package com.build4all.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

public enum BookingStatus {
    PENDING("Pending"),
    APPROVED("Approved"),
    REJECTED("Rejected"),
    CANCEL_REQUESTED("Cancel_Requested"),
    CANCELED("Canceled"),
    COMPLETED("Completed"),
    REFUNDED("Refunded");

    private final String apiValue;
    BookingStatus(String apiValue) { this.apiValue = apiValue; }

    @JsonValue
    public String getApiValue() { return apiValue; }

    @JsonCreator
    public static BookingStatus fromValue(String value) {
        String v = value.trim();
        return Arrays.stream(values())
            .filter(s -> s.apiValue.equalsIgnoreCase(v) || s.name().equalsIgnoreCase(v))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown status: " + value));
    }
}
