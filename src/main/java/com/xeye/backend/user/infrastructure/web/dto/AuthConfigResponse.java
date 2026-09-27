package com.xeye.backend.user.infrastructure.web.dto;

import java.util.List;

/** Lo que la consola necesita saber antes de mostrar los formularios de acceso. */
public record AuthConfigResponse(boolean emailVerificationRequired, List<String> ssoProviders,
                                 String captchaProvider, String captchaSiteKey) {
}
