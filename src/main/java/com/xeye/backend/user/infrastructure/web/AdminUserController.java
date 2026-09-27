package com.xeye.backend.user.infrastructure.web;

import com.xeye.backend.shared.security.AuthenticatedUser;
import com.xeye.backend.user.application.command.AdminUpdateUserCommand;
import com.xeye.backend.user.application.port.in.AdminUserUseCases;
import com.xeye.backend.user.domain.model.Permission;
import com.xeye.backend.user.infrastructure.web.dto.AdminUpdateUserRequest;
import com.xeye.backend.user.infrastructure.web.dto.AdminUserPageResponse;
import com.xeye.backend.user.infrastructure.web.dto.AdminUserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administración de cuentas. Doble barrera: {@code /admin/**} exige ROLE_ADMIN en
 * {@code SecurityConfig} y cada método lo repite con {@code @PreAuthorize}.
 */
@RestController
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserUseCases admin;

    public AdminUserController(AdminUserUseCases admin) {
        this.admin = admin;
    }

    @GetMapping
    public AdminUserPageResponse list(@RequestParam(defaultValue = "0") int offset,
                                      @RequestParam(defaultValue = "50") int limit) {
        return AdminUserPageResponse.from(admin.list(offset, limit));
    }

    @GetMapping("/{id}")
    public AdminUserResponse get(@PathVariable Long id) {
        return AdminUserResponse.from(admin.get(id));
    }

    @PutMapping("/{id}")
    public AdminUserResponse update(@AuthenticationPrincipal AuthenticatedUser current, @PathVariable Long id,
                                    @Valid @RequestBody AdminUpdateUserRequest request) {
        return AdminUserResponse.from(admin.update(current.id(), id, new AdminUpdateUserCommand(
                request.permission() == null ? null : Permission.fromString(request.permission()),
                request.emailVerified(), request.unlock(),
                request.searchRateLimitPerMinute(), request.resetSearchRateLimit())));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser current, @PathVariable Long id) {
        admin.delete(current.id(), id);
    }

    @PostMapping("/{id}/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logoutAll(@PathVariable Long id) {
        admin.logoutAll(id);
    }
}
