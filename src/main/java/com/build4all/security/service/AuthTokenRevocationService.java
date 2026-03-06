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
    public void revokeNow(String subjectType, Long subjectId, Long ownerProjectId) {
        final LocalDateTime now = LocalDateTime.now();

        AuthTokenRevocation row = (ownerProjectId == null)
                ? repo.findBySubjectTypeAndSubjectIdAndOwnerProjectIdIsNull(subjectType, subjectId)
                    .orElseGet(() -> {
                        AuthTokenRevocation r = new AuthTokenRevocation();
                        r.setSubjectType(subjectType);
                        r.setSubjectId(subjectId);
                        r.setOwnerProjectId(null);
                        return r;
                    })
                : repo.findBySubjectTypeAndSubjectIdAndOwnerProjectId(subjectType, subjectId, ownerProjectId)
                    .orElseGet(() -> {
                        AuthTokenRevocation r = new AuthTokenRevocation();
                        r.setSubjectType(subjectType);
                        r.setSubjectId(subjectId);
                        r.setOwnerProjectId(ownerProjectId);
                        return r;
                    });

        row.setRevokedAfter(now);
        repo.save(row);
    }

    public boolean isRevoked(String subjectType, Long subjectId, Long ownerProjectId, Date issuedAt) {
        if (subjectType == null || subjectType.isBlank()) return false;
        if (subjectId == null) return false;
        if (issuedAt == null) return false;

        var rowOpt = (ownerProjectId == null)
                ? repo.findBySubjectTypeAndSubjectIdAndOwnerProjectIdIsNull(subjectType, subjectId)
                : repo.findBySubjectTypeAndSubjectIdAndOwnerProjectId(subjectType, subjectId, ownerProjectId);

        if (rowOpt.isEmpty()) return false;

        var revokedAfter = rowOpt.get().getRevokedAfter();
        if (revokedAfter == null) return false;

        var iatInstant = issuedAt.toInstant();
        var revokedInstant = revokedAfter.atZone(ZoneId.systemDefault()).toInstant();

        return !iatInstant.isAfter(revokedInstant); // iat <= revokedAfter
    }
}