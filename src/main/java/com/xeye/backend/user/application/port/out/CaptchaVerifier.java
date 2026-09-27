package com.xeye.backend.user.application.port.out;

/** CAPTCHA opcional en registro/login. Con el proveedor {@code none} siempre pasa. */
public interface CaptchaVerifier {

    boolean enabled();

    boolean verify(String token, String remoteIp);
}
