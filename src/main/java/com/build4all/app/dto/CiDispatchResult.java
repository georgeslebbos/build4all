package com.build4all.app.dto;

public record CiDispatchResult(
        boolean ok,
        int httpCode,
        String responseBody,
        String buildId
) {}
