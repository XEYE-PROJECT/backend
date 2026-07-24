package com.xeye.backend.list.application.command;

public record CreateListCommand(String name, String description, boolean isPublic) {
}
