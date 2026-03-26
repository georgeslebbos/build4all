package com.build4all.app.internaltesting.domain;

public enum IosInternalTestingRequestStatus {
    REQUESTED,
    PROCESSING,
    INVITED_TO_APPLE_TEAM,
    WAITING_OWNER_ACCEPTANCE,
    WAITING_APPLE_USER_SYNC,
    ADDING_TO_INTERNAL_TESTING,
    READY,
    FAILED,
    CANCELLED,
    MANUAL_REVIEW_REQUIRED;

    public boolean isFinalStatus() {
        return this == READY
                || this == FAILED
                || this == CANCELLED;
    }

    public boolean isWaitingStatus() {
        return this == INVITED_TO_APPLE_TEAM
                || this == WAITING_OWNER_ACCEPTANCE
                || this == WAITING_APPLE_USER_SYNC
                || this == ADDING_TO_INTERNAL_TESTING;
    }

    public boolean consumesSlot() {
        return this == REQUESTED
                || this == PROCESSING
                || this == INVITED_TO_APPLE_TEAM
                || this == WAITING_OWNER_ACCEPTANCE
                || this == WAITING_APPLE_USER_SYNC
                || this == ADDING_TO_INTERNAL_TESTING
                || this == MANUAL_REVIEW_REQUIRED
                || this == READY;
    }
}