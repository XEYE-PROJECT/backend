package com.xeye.backend.user.application.service;

import com.xeye.backend.shared.event.UserDeletedEvent;
import com.xeye.backend.shared.event.UserSearchLimitChangedEvent;
import com.xeye.backend.shared.exception.BadRequestException;
import com.xeye.backend.shared.exception.NotFoundException;
import com.xeye.backend.shared.security.AuthAuditLog;
import com.xeye.backend.user.application.command.AdminUpdateUserCommand;
import com.xeye.backend.user.application.command.UserPage;
import com.xeye.backend.user.application.port.in.AdminUserUseCases;
import com.xeye.backend.user.application.port.in.UserQueryPort;
import com.xeye.backend.user.application.port.out.SessionRevoker;
import com.xeye.backend.user.application.port.out.UserRepository;
import com.xeye.backend.user.domain.model.Permission;
import com.xeye.backend.user.domain.model.User;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/** Administración de cuentas. La autorización (ROLE_ADMIN) la aplica el controlador con {@code @PreAuthorize}. */
@Service
public class AdminUserService implements AdminUserUseCases, UserQueryPort {

    private final UserRepository users;
    private final SessionRevoker sessionRevoker;
    private final ApplicationEventPublisher events;

    public AdminUserService(UserRepository users, SessionRevoker sessionRevoker, ApplicationEventPublisher events) {
        this.users = users;
        this.sessionRevoker = sessionRevoker;
        this.events = events;
    }

    @Override
    @Transactional(readOnly = true)
    public UserPage list(int offset, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        int safeOffset = Math.max(0, offset);
        return new UserPage(users.findPage(safeOffset, safeLimit), users.count(), safeOffset, safeLimit);
    }

    @Override
    @Transactional(readOnly = true)
    public User get(Long userId) {
        return users.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
    }

    @Override
    @Transactional
    public User update(Long actorId, Long userId, AdminUpdateUserCommand command) {
        User user = get(userId);
        if (command.permission() != null) {
            if (userId.equals(actorId) && command.permission() != Permission.ADMIN) {
                throw new BadRequestException("You cannot remove your own admin role");
            }
            user.promoteTo(command.permission());
            sessionRevoker.versionChanged(userId);
            user.invalidateSessions(); // el rol viaja en el JWT: obliga a un token nuevo
        }
        if (Boolean.TRUE.equals(command.emailVerified())) {
            user.markEmailVerified();
        }
        if (Boolean.TRUE.equals(command.unlock())) {
            user.unlock();
        }
        Integer previousLimit = user.searchRateLimitPerMinute();
        if (Boolean.TRUE.equals(command.resetSearchRateLimit())) {
            user.changeSearchRateLimit(null);
        } else if (command.searchRateLimitPerMinute() != null) {
            user.changeSearchRateLimit(command.searchRateLimitPerMinute());
        }
        User saved = users.save(user);
        if (!Objects.equals(previousLimit, saved.searchRateLimitPerMinute())) {
            // El buscador aplica el cupo: se lo comunicamos tras el commit (best-effort, el
            // bootstrap periódico lo cura si el aviso se pierde).
            events.publishEvent(new UserSearchLimitChangedEvent(userId, saved.searchRateLimitPerMinute()));
        }
        AuthAuditLog.info("admin_user_updated", "-", user.email(), "actorId=" + actorId + " userId=" + userId
                + (Objects.equals(previousLimit, saved.searchRateLimitPerMinute()) ? ""
                : " searchRateLimitPerMinute=" + saved.searchRateLimitPerMinute()));
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserSearchLimit> findSearchRateLimits() {
        return users.findWithSearchRateLimit().stream()
                .map(user -> new UserSearchLimit(user.id(), user.searchRateLimitPerMinute()))
                .toList();
    }

    @Override
    @Transactional
    public void delete(Long actorId, Long userId) {
        if (userId.equals(actorId)) {
            throw new BadRequestException("Delete your own account from the account page");
        }
        User user = get(userId);
        users.deleteById(userId);
        sessionRevoker.versionChanged(userId);
        events.publishEvent(new UserDeletedEvent(userId));
        AuthAuditLog.info("admin_user_deleted", "-", user.email(), "actorId=" + actorId + " userId=" + userId);
    }

    @Override
    @Transactional
    public void logoutAll(Long userId) {
        User user = get(userId);
        user.invalidateSessions();
        users.save(user);
        sessionRevoker.versionChanged(userId);
        AuthAuditLog.info("admin_logout_all", "-", user.email(), "userId=" + userId);
    }
}
