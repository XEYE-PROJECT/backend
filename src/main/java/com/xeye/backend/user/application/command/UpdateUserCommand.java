package com.xeye.backend.user.application.command;

/** Actualización parcial: los campos {@code null} no se modifican. */
public record UpdateUserCommand(String name, String surname, String email, String rawPassword) {
}
