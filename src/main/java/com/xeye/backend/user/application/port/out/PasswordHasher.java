package com.xeye.backend.user.application.port.out;

/** Puerto de salida para el hasheo de contraseñas (adaptador BCrypt en infraestructura). */
public interface PasswordHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String hashedPassword);
}
