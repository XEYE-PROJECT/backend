package com.xeye.backend.training.infrastructure.web.dto;

/** Respuesta del webhook: {@code applied} = false si el callback se ignoró (duplicado o fuera de orden). */
public record WebhookAck(boolean success, String message, boolean applied) {
}
