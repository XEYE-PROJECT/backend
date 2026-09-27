package com.xeye.backend.user.application.command;

import com.xeye.backend.user.domain.model.Permission;

/** Cambios que un administrador puede aplicar a otra cuenta (los {@code null} no se tocan). */
public record AdminUpdateUserCommand(Permission permission, Boolean emailVerified, Boolean unlock) {
}
