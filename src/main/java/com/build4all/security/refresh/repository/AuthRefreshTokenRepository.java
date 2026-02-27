package com.build4all.security.refresh.repository;



import com.build4all.security.refresh.AuthRefreshToken;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthRefreshTokenRepository extends JpaRepository<AuthRefreshToken, Long> {
    Optional<AuthRefreshToken> findByTokenHash(String tokenHash);
}