package com.xeye.backend.user.application.command;

/** Identidad verificada por un proveedor OIDC (Google, Microsoft). */
public record SsoIdentity(String provider, String subject, String email, boolean emailVerified,
                          String name, String surname) {
}
