package com.xeye.backend.user.application.command;

public record LoginCommand(String email, String rawPassword) {
}
