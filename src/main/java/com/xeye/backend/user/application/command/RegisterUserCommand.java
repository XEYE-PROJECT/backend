package com.xeye.backend.user.application.command;

public record RegisterUserCommand(String name, String surname, String email, String rawPassword) {
}
