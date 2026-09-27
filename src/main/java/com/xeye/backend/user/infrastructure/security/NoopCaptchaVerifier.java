package com.xeye.backend.user.infrastructure.security;

import com.xeye.backend.user.application.port.out.CaptchaVerifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Sin CAPTCHA (por defecto). */
@Component
@ConditionalOnProperty(name = "xeye.auth.captcha.provider", havingValue = "none", matchIfMissing = true)
public class NoopCaptchaVerifier implements CaptchaVerifier {

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public boolean verify(String token, String remoteIp) {
        return true;
    }
}
