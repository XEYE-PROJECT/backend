package com.xeye.backend.user.application.command;

public record ChangePasswordCommand(String currentPassword, String newPassword) {
}
