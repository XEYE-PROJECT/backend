package com.xeye.backend.user.infrastructure.web.dto;

import java.util.List;

/** Códigos de recuperación en claro: la ÚNICA vez que existen (después solo su hash). */
public record RecoveryCodesResponse(List<String> recoveryCodes) {
}
