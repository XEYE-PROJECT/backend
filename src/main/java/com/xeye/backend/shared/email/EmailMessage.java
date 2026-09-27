package com.xeye.backend.shared.email;

/** Un email transaccional listo para enviar (texto plano + HTML). */
public record EmailMessage(String to, String subject, String text, String html) {
}
