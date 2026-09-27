package com.xeye.backend.shared.email;

import com.xeye.backend.shared.config.EmailProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Proveedor {@code smtp}: envía por el {@link JavaMailSender} autoconfigurado con
 * {@code spring.mail.*} (IONOS: {@code smtp.ionos.es:587} con STARTTLS, usuario = el buzón
 * completo). Texto plano + HTML. Los fallos se loguean y no se propagan (el usuario puede
 * pedir un reenvío); la contraseña del buzón nunca aparece en el log.
 */
@Component
@ConditionalOnProperty(name = "xeye.email.provider", havingValue = "smtp")
public class SmtpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final JavaMailSender mailSender;
    private final String from;
    private final String replyTo;

    public SmtpEmailSender(JavaMailSender mailSender, EmailProperties properties) {
        this.mailSender = mailSender;
        this.from = properties.from();
        this.replyTo = properties.replyTo();
    }

    @Override
    public void send(EmailMessage message) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            if (!replyTo.isBlank()) {
                helper.setReplyTo(replyTo);
            }
            helper.setTo(message.to());
            helper.setSubject(message.subject());
            helper.setText(message.text(), message.html());
            mailSender.send(mime);
            log.info("Email sent via SMTP to {} ({})", message.to(), message.subject());
        } catch (MessagingException | RuntimeException e) {
            log.error("Failed to send email to {} ({}): {}", message.to(), message.subject(), e.getMessage());
        }
    }
}
