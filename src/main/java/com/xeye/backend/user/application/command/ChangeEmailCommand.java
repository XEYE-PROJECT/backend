package com.xeye.backend.user.application.command;

public record ChangeEmailCommand(String newEmail, String currentPassword) {
}
