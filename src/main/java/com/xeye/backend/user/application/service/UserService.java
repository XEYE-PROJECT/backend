package com.xeye.backend.user.application.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xeye.backend.shared.event.UserDeletedEvent;
import com.xeye.backend.shared.exception.BadRequestException;
import com.xeye.backend.shared.exception.ConflictException;
import com.xeye.backend.shared.exception.NotFoundException;
import com.xeye.backend.shared.exception.UnauthorizedException;
import com.xeye.backend.user.application.command.AuthResult;
import com.xeye.backend.user.application.command.LoginCommand;
import com.xeye.backend.user.application.command.RegisterUserCommand;
import com.xeye.backend.user.application.command.UpdateUserCommand;
import com.xeye.backend.user.application.port.in.UserUseCases;
import com.xeye.backend.user.application.port.out.PasswordHasher;
import com.xeye.backend.user.application.port.out.TokenIssuer;
import com.xeye.backend.user.application.port.out.UserRepository;
import com.xeye.backend.user.domain.model.User;

@Service
public class UserService implements UserUseCases {

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final UserRepository users;
    private final PasswordHasher passwordHasher;
    private final TokenIssuer tokenIssuer;
    private final ApplicationEventPublisher events;

    public UserService(UserRepository users, PasswordHasher passwordHasher, TokenIssuer tokenIssuer,
                       ApplicationEventPublisher events) {
        this.users = users;
        this.passwordHasher = passwordHasher;
        this.tokenIssuer = tokenIssuer;
        this.events = events;
    }

    @Override
    @Transactional
    public AuthResult register(RegisterUserCommand command) {
        String email = normalizeEmail(command.email());
        requireStrongPassword(command.rawPassword());
        if (users.existsByEmail(email)) {
            throw new ConflictException("Email already registered");
        }
        User user = User.register(
                command.name(), command.surname(), email, passwordHasher.hash(command.rawPassword()));
        return issueFor(users.save(user));
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResult login(LoginCommand command) {
        User user = users.findByEmail(normalizeEmail(command.email()))
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));
        if (!passwordHasher.matches(command.rawPassword(), user.password())) {
            throw new UnauthorizedException("Invalid credentials");
        }
        return issueFor(user);
    }

    @Override
    @Transactional(readOnly = true)
    public User getById(Long userId) {
        return users.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    @Override
    @Transactional
    public User update(Long userId, UpdateUserCommand command) {
        User user = getById(userId);
        if (command.name() != null || command.surname() != null) {
            user.rename(
                    command.name() != null ? command.name() : user.name(),
                    command.surname() != null ? command.surname() : user.surname());
        }
        if (command.email() != null) {
            String email = normalizeEmail(command.email());
            users.findByEmail(email)
                    .filter(existing -> !existing.id().equals(userId))
                    .ifPresent(existing -> {
                        throw new ConflictException("Email already registered");
                    });
            user.changeEmail(email);
        }
        if (command.rawPassword() != null) {
            requireStrongPassword(command.rawPassword());
            user.changePassword(passwordHasher.hash(command.rawPassword()));
        }
        return users.save(user);
    }

    @Override
    @Transactional
    public void delete(Long userId) {
        getById(userId); // 404 si no existe
        users.deleteById(userId);
        // La BD borra en cascada api_keys/lists/elements; el search-service invalida sus cachés con este evento.
        events.publishEvent(new UserDeletedEvent(userId));
    }

    private AuthResult issueFor(User user) {
        return new AuthResult(user, tokenIssuer.issue(user), tokenIssuer.expiresInMinutes());
    }

    private void requireStrongPassword(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new BadRequestException("Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new BadRequestException("Email must not be blank");
        }
        return email.trim().toLowerCase();
    }
}
