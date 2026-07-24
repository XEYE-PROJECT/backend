package com.xeye.backend.apikey.infrastructure;

import com.xeye.backend.apikey.application.port.out.ApiKeyGenerator;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/** Genera claves con la forma {@code xeye_<32 caracteres base64 url-safe>}. */
@Component
public class SecureRandomApiKeyGenerator implements ApiKeyGenerator {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final SecureRandom random = new SecureRandom();

    @Override
    public String generate() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return "xeye_" + ENCODER.encodeToString(bytes);
    }
}
