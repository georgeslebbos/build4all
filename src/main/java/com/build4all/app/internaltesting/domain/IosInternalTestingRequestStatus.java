package com.build4all.app.internaltesting.domain;

public enum IosInternalTestingRequestStatus {
    REQUESTED,
    PROCESSING,
    INVITED_TO_APPLE_TEAM,
    WAITING_OWNER_ACCEPTANCE,
    ADDING_TO_INTERNAL_TESTING,
    READY,
    FAILED,
    CANCELLED,
	MANUAL_REVIEW_REQUIRED;

    public boolean isFinalStatus() {
        return this == READY || this == FAILED || this == CANCELLED;
    }

    public boolean isWaitingStatus() {
        return this == INVITED_TO_APPLE_TEAM || this == WAITING_OWNER_ACCEPTANCE;
    }
}