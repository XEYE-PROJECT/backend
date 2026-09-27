package com.xeye.backend.shared.email;

/** Puerto de envío de email transaccional. Las implementaciones no deben lanzar por fallos de red: loguean. */
public interface EmailSender {

    void send(EmailMessage message);
}
