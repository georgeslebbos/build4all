package com.build4all.ai.dto;

public record AiStatusResponse(
    Long linkId,
    Long ownerId,
    String ownerName,
    boolean aiEnabled
) {}
