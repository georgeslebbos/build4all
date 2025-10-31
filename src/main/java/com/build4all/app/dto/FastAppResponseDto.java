// src/main/java/com/build4all/app/dto/FastAppResponseDto.java
package com.build4all.app.dto;

import java.time.LocalDate;

public record FastAppResponseDto(
    Long ownerId,
    Long projectId,
    String slug,
    String ownerProjectLinkId,
    String apkUrl,
    LocalDate validFrom,
    LocalDate endTo
) {}
