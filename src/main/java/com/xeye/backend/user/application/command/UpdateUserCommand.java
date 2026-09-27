package com.xeye.backend.user.application.command;

/** Actualización parcial del perfil: los campos {@code null} no se modifican. El email y la contraseña tienen sus propios flujos. */
public record UpdateUserCommand(String name, String surname, String locale) {
}
