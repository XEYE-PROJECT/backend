package com.xeye.backend.element.application.command;

/** Actualización parcial: los campos {@code null} no se modifican. */
public record UpdateElementCommand(String text, String params, String description) {
}
