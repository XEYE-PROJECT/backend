package com.xeye.backend.user.application.command;

public record ResetPasswordCommand(String token, String newPassword) {
}
