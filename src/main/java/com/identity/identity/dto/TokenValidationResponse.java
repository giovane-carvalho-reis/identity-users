package com.identity.identity.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

public record TokenValidationResponse(
        boolean valid,
        String subject,
        String username,
        String email,
        List<String> roles,
        @JsonProperty("issued_at")  Instant issuedAt,
        @JsonProperty("expires_at") Instant expiresAt
) {}

