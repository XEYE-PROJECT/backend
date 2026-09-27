package com.xeye.backend.user.infrastructure.security;

import com.xeye.backend.user.application.port.out.BreachedPasswordChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Have I Been Pwned "Pwned Passwords" con k-anonimato: solo se envían los 5 primeros hex del
 * SHA-1 de la contraseña y se busca el sufijo en la respuesta. Ante cualquier fallo de red
 * responde "no filtrada" (falla abierto: un tercero caído no debe impedir registrarse).
 */
@Component
@ConditionalOnProperty(name = "xeye.auth.password.breach-check", havingValue = "hibp", matchIfMissing = true)
public class HibpBreachedPasswordChecker implements BreachedPasswordChecker {

    private static final Logger log = LoggerFactory.getLogger(HibpBreachedPasswordChecker.class);

    private final RestClient http;

    public HibpBreachedPasswordChecker() {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        this.http = RestClient.builder()
                .baseUrl("https://api.pwnedpasswords.com")
                .requestFactory(requestFactory)
                .defaultHeader("Add-Padding", "true")
                .defaultHeader("User-Agent", "xeye-backend")
                .build();
    }

    @Override
    public boolean isBreached(String rawPassword) {
        String sha1 = sha1Hex(rawPassword);
        String prefix = sha1.substring(0, 5);
        String suffix = sha1.substring(5);
        try {
            String body = http.get().uri("/range/{prefix}", prefix).retrieve().body(String.class);
            if (body == null) {
                return false;
            }
            for (String line : body.split("\r?\n")) {
                int colon = line.indexOf(':');
                if (colon > 0 && line.substring(0, colon).equalsIgnoreCase(suffix)) {
                    return !"0".equals(line.substring(colon + 1).trim()); // el padding añade sufijos con 0
                }
            }
            return false;
        } catch (RuntimeException e) {
            log.warn("HIBP check unavailable, allowing password ({})", e.getClass().getSimpleName());
            return false;
        }
    }

    private static String sha1Hex(String value) {
        try {
            return HexFormat.of().withUpperCase().formatHex(
                    MessageDigest.getInstance("SHA-1").digest(value.getBytes(StandardCharsets.UTF_8))).toUpperCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
