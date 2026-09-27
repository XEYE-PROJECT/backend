package com.xeye.backend.shared.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Locale;

/**
 * TOTP (RFC 6238) sobre HMAC-SHA1, 6 dígitos, paso de 30 s, compatible con Google Authenticator,
 * Authy, 1Password, etc. Java puro, sin dependencias.
 */
public final class Totp {

    public static final int DIGITS = 6;
    public static final int PERIOD_SECONDS = 30;
    /** Ventana de tolerancia: se aceptan el paso actual y ±1 (desfase de reloj). */
    public static final int WINDOW = 1;

    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();

    private Totp() {
    }

    /** Secreto nuevo de 160 bits codificado en base32 (formato que esperan las apps). */
    public static String generateSecret() {
        byte[] bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        return base32Encode(bytes);
    }

    /** URI {@code otpauth://} que las apps leen del QR. */
    public static String otpauthUri(String issuer, String account, String secret) {
        String label = URLEncoder.encode(issuer + ":" + account, StandardCharsets.UTF_8).replace("+", "%20");
        return "otpauth://totp/" + label + "?secret=" + secret
                + "&issuer=" + URLEncoder.encode(issuer, StandardCharsets.UTF_8).replace("+", "%20")
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + PERIOD_SECONDS;
    }

    /** Código para el paso de tiempo que contiene {@code epochSeconds}. */
    public static String codeAt(String secret, long epochSeconds) {
        return codeForCounter(secret, Math.floorDiv(epochSeconds, PERIOD_SECONDS));
    }

    /** Comprueba un código con tolerancia de ±{@link #WINDOW} pasos. Nulo/mal formado → false. */
    public static boolean verify(String secret, String code, long epochSeconds) {
        if (secret == null || code == null) {
            return false;
        }
        String candidate = code.replace(" ", "").trim();
        if (candidate.length() != DIGITS || !candidate.chars().allMatch(Character::isDigit)) {
            return false;
        }
        long counter = Math.floorDiv(epochSeconds, PERIOD_SECONDS);
        boolean match = false;
        for (int offset = -WINDOW; offset <= WINDOW; offset++) {
            // Sin cortocircuito: se recorren siempre todas las ventanas (tiempo constante).
            match |= SecretTokens.constantTimeEquals(codeForCounter(secret, counter + offset), candidate);
        }
        return match;
    }

    static String codeForCounter(String secret, long counter) {
        byte[] key = base32Decode(secret);
        byte[] message = new byte[8];
        for (int i = 7; i >= 0; i--) {
            message[i] = (byte) (counter & 0xff);
            counter >>= 8;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(message);
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
            int otp = binary % 1_000_000;
            return String.format(Locale.ROOT, "%06d", otp);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA1 unavailable", e);
        }
    }

    static String base32Encode(byte[] data) {
        StringBuilder out = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                out.append(BASE32.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) {
            out.append(BASE32.charAt((buffer << (5 - bits)) & 31));
        }
        return out.toString();
    }

    static byte[] base32Decode(String encoded) {
        String clean = encoded.replace("=", "").replace(" ", "").toUpperCase(Locale.ROOT);
        byte[] out = new byte[clean.length() * 5 / 8];
        int buffer = 0;
        int bits = 0;
        int index = 0;
        for (char c : clean.toCharArray()) {
            int value = BASE32.indexOf(c);
            if (value < 0) {
                throw new IllegalArgumentException("Invalid base32 character: " + c);
            }
            buffer = (buffer << 5) | value;
            bits += 5;
            if (bits >= 8) {
                out[index++] = (byte) ((buffer >> (bits - 8)) & 0xff);
                bits -= 8;
            }
        }
        return out;
    }
}
