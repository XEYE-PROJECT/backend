package com.xeye.backend.list.application.command;

/** Actualización parcial: los campos {@code null} no se modifican. */
public record UpdateListCommand(String name, String description, Boolean isPublic) {
}
