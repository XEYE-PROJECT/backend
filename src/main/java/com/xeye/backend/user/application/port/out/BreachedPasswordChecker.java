package com.xeye.backend.user.application.port.out;

/** Comprueba si una contraseña aparece en filtraciones conocidas (HIBP). Debe fallar abierto. */
public interface BreachedPasswordChecker {

    boolean isBreached(String rawPassword);
}
