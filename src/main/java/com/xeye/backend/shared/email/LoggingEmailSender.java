package com.xeye.backend.shared.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Proveedor {@code log}: no envía nada, imprime el email en el log (asunto, destinatario y el
 * texto plano, que contiene los enlaces). Es el modo de desarrollo: los enlaces de verificación y
 * de recuperación se copian del log del backend.
 */
@Component
@ConditionalOnProperty(name = "xeye.email.provider", havingValue = "log")
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(EmailMessage message) {
        log.info("\n==================== EMAIL (provider=log, not sent) ====================\n"
                + "To: {}\nSubject: {}\n\n{}\n"
                + "========================================================================",
                message.to(), message.subject(), message.text());
    }
}
