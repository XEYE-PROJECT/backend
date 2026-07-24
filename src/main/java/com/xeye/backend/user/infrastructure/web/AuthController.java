package com.xeye.backend.user.infrastructure.web;

import com.xeye.backend.user.application.command.LoginCommand;
import com.xeye.backend.user.application.command.RegisterUserCommand;
import com.xeye.backend.user.application.port.in.UserUseCases;
import com.xeye.backend.user.infrastructure.web.dto.AuthResponse;
import com.xeye.backend.user.infrastructure.web.dto.LoginRequest;
import com.xeye.backend.user.infrastructure.web.dto.RegisterRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints públicos de autenticación; ambos devuelven token (register hace auto-login). */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserUseCases users;

    public AuthController(UserUseCases users) {
        this.users = users;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return AuthResponse.of(users.register(new RegisterUserCommand(
                request.name(), request.surname(), request.email(), request.password())));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return AuthResponse.of(users.login(new LoginCommand(request.email(), request.password())));
    }
}
