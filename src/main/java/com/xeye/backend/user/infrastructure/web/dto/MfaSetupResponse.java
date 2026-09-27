package com.xeye.backend.user.infrastructure.web.dto;

public record MfaSetupResponse(String secret, String otpauthUri) {
}
