package com.xeye.backend.user.application.service;

import com.xeye.backend.shared.email.EmailMessage;

/** Plantillas de los emails de cuenta en ES/EN. Texto plano (con el enlace) + HTML mínimo. */
final class EmailTemplates {

    private EmailTemplates() {
    }

    static EmailMessage verifyEmail(String to, String locale, String link) {
        return "en".equals(locale)
                ? build(to, "Verify your XEYE email",
                        "Welcome to XEYE!", "Confirm your email address to activate your account. The link expires in 24 hours.",
                        "Verify email", link, "If you did not create an account, ignore this email.")
                : build(to, "Verifica tu email de XEYE",
                        "¡Bienvenido a XEYE!", "Confirma tu dirección de email para activar tu cuenta. El enlace caduca en 24 horas.",
                        "Verificar email", link, "Si no has creado ninguna cuenta, ignora este email.");
    }

    static EmailMessage accountExists(String to, String locale, String loginLink, String resetLink) {
        return "en".equals(locale)
                ? build(to, "You already have a XEYE account",
                        "Someone tried to sign up with this email", "You already have an account at XEYE. You can sign in, or reset your password if you forgot it.",
                        "Sign in", loginLink, "Reset password: " + resetLink)
                : build(to, "Ya tienes una cuenta en XEYE",
                        "Alguien ha intentado registrarse con este email", "Ya tienes una cuenta en XEYE. Puedes iniciar sesión o restablecer tu contraseña si la has olvidado.",
                        "Iniciar sesión", loginLink, "Restablecer contraseña: " + resetLink);
    }

    static EmailMessage resetPassword(String to, String locale, String link) {
        return "en".equals(locale)
                ? build(to, "Reset your XEYE password",
                        "Password reset", "Use the link below to set a new password. It expires in 1 hour and can be used once.",
                        "Set a new password", link, "If you did not request this, ignore this email: your password stays the same.")
                : build(to, "Restablece tu contraseña de XEYE",
                        "Restablecer contraseña", "Usa el enlace para fijar una contraseña nueva. Caduca en 1 hora y solo sirve una vez.",
                        "Nueva contraseña", link, "Si no lo has pedido tú, ignora este email: tu contraseña no cambia.");
    }

    static EmailMessage changeEmail(String to, String locale, String link) {
        return "en".equals(locale)
                ? build(to, "Confirm your new XEYE email",
                        "Confirm your new email", "Confirm this address to use it for your XEYE account. The link expires in 1 hour. All your sessions will be closed afterwards.",
                        "Confirm new email", link, "If you did not request this change, ignore this email.")
                : build(to, "Confirma tu nuevo email de XEYE",
                        "Confirma tu nuevo email", "Confirma esta dirección para usarla en tu cuenta de XEYE. El enlace caduca en 1 hora. Después se cerrarán todas tus sesiones.",
                        "Confirmar nuevo email", link, "Si no has pedido este cambio, ignora este email.");
    }

    private static EmailMessage build(String to, String subject, String title, String body,
                                      String cta, String link, String footer) {
        String text = title + "\n\n" + body + "\n\n" + cta + ": " + link + "\n\n" + footer + "\n";
        String html = "<!doctype html><html><body style=\"font-family:system-ui,sans-serif;color:#111;max-width:520px;margin:0 auto;padding:24px\">"
                + "<h2 style=\"margin:0 0 12px\">" + escape(title) + "</h2>"
                + "<p>" + escape(body) + "</p>"
                + "<p style=\"margin:24px 0\"><a href=\"" + escape(link) + "\" style=\"background:#4f46e5;color:#fff;padding:10px 18px;border-radius:8px;text-decoration:none;display:inline-block\">"
                + escape(cta) + "</a></p>"
                + "<p style=\"font-size:13px;color:#555\">" + escape(footer) + "</p>"
                + "<p style=\"font-size:12px;color:#888;word-break:break-all\">" + escape(link) + "</p>"
                + "</body></html>";
        return new EmailMessage(to, subject, text, html);
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
