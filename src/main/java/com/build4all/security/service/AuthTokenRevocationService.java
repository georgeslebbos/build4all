package com.build4all.security.service;

import com.build4all.security.domain.AuthTokenRevocation;
import com.build4all.security.repository.AuthTokenRevocationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

@Service
public class AuthTokenRevocationService {

    private final AuthTokenRevocationRepository repo;

    public AuthTokenRevocationService(AuthTokenRevocationRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public void revokeNow(String subjectType, Long subjectId) {
        final LocalDateTime now = LocalDateTime.now();

        AuthTokenRevocation row = repo.findBySubjectTypeAndSubjectId(subjectType, subjectId)
                .orElseGet(() -> {
                    AuthTokenRevocation r = new AuthTokenRevocation();
                    r.setSubjectType(subjectType);
                    r.setSubjectId(subjectId);
                    return r;
                });

        row.setRevokedAfter(now);
        repo.save(row);
    }

    public boolean isRevoked(String subjectType, Long subjectId, Date issuedAt) {
        if (subjectType == null || subjectType.isBlank()) return false;
        if (subjectId == null) return false;
        if (issuedAt == null) return false;

        var rowOpt = repo.findBySubjectTypeAndSubjectId(subjectType, subjectId);
        if (rowOpt.isEmpty()) return false;

        var revokedAfter = rowOpt.get().getRevokedAfter();
        if (revokedAfter == null) return false;

        var iatInstant = issuedAt.toInstant();
        var revokedInstant = revokedAfter.atZone(ZoneId.systemDefault()).toInstant();

        return !iatInstant.isAfter(revokedInstant); // iat <= revokedAfter
    }
}