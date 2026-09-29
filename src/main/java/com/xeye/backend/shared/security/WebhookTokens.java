package com.xeye.backend.shared.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Token de webhook <em>por entrenamiento</em>: {@code <trainingId>.<hex(HMAC-SHA256(secreto,
 * "training-webhook:" + trainingId))>}. El backend lo deriva del secreto compartido al lanzar y
 * lo mete en el job; el worker lo devuelve en {@code X-Webhook-Token}. Así el secreto nunca sale
 * del backend (ni al fichero del job, ni al endpoint de RunPod) y un token filtrado solo sirve
 * para reportar sobre <em>ese</em> entrenamiento. Sin estado: se verifica recalculando el HMAC.
 */
public final class WebhookTokens {

    private static final String ALGORITHM = "HmacSHA256";
    private static final String PURPOSE = "training-webhook:";

    private WebhookTokens() {
    }

    public static String issue(String secret, long trainingId) {
        return trainingId + "." + HexFormat.of().formatHex(hmac(secret, PURPOSE + trainingId));
    }

    /** El id de entrenamiento que firma el token, o vacío si el token no es válido (comparación en tiempo constante). */
    public static Optional<Long> verify(String secret, String token) {
        if (secret == null || secret.isBlank() || token == null) {
            return Optional.empty();
        }
        int dot = token.indexOf('.');
        if (dot <= 0 || dot == token.length() - 1) {
            return Optional.empty();
        }
        long trainingId;
        try {
            trainingId = Long.parseLong(token.substring(0, dot));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
        byte[] provided;
        try {
            provided = HexFormat.of().parseHex(token.substring(dot + 1));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
        byte[] expected = hmac(secret, PURPOSE + trainingId);
        return MessageDigest.isEqual(expected, provided) ? Optional.of(trainingId) : Optional.empty();
    }

    private static byte[] hmac(String secret, String message) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalStateException("HMAC-SHA256 is not available in this JVM", ex);
        }
    }
}
