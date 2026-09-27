package com.xeye.backend.user.application.command;

import com.xeye.backend.user.domain.model.User;

import java.util.List;

public record UserPage(List<User> items, long total, int offset, int limit) {
}
