package com.xeye.backend.user.infrastructure.web.dto;

import com.xeye.backend.user.application.command.UserPage;

import java.util.List;

public record AdminUserPageResponse(List<AdminUserResponse> items, long total, int offset, int limit) {

    public static AdminUserPageResponse from(UserPage page) {
        return new AdminUserPageResponse(page.items().stream().map(AdminUserResponse::from).toList(),
                page.total(), page.offset(), page.limit());
    }
}
