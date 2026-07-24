package com.xeye.backend.user.infrastructure.web;

import com.xeye.backend.shared.security.AuthenticatedUser;
import com.xeye.backend.user.application.command.UpdateUserCommand;
import com.xeye.backend.user.application.port.in.UserUseCases;
import com.xeye.backend.user.infrastructure.web.dto.UpdateUserRequest;
import com.xeye.backend.user.infrastructure.web.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** El usuario autenticado solo gestiona su propia cuenta (/users/me). */
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserUseCases users;

    public UserController(UserUseCases users) {
        this.users = users;
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser current) {
        return UserResponse.from(users.getById(current.id()));
    }

    @PutMapping("/me")
    public UserResponse update(@AuthenticationPrincipal AuthenticatedUser current,
                               @Valid @RequestBody UpdateUserRequest request) {
        return UserResponse.from(users.update(current.id(), new UpdateUserCommand(
                request.name(), request.surname(), request.email(), request.password())));
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser current) {
        users.delete(current.id());
    }
}
