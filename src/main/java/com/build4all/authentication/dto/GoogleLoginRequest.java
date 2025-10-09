package com.build4all.authentication.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public class GoogleLoginRequest {

    @Schema(description = "Google ID token", example = "eyJhbGciOiJSUzI1NiIsIn...")
    private String idToken;

    public String getIdToken() {
        return idToken;
    }

    public void setIdToken(String idToken) {
        this.idToken = idToken;
    }
}
