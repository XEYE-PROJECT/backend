package com.xeye.backend.apikey.infrastructure.web;

import com.xeye.backend.apikey.application.port.in.ApiKeyUseCases;
import com.xeye.backend.apikey.infrastructure.web.dto.ApiKeyResponse;
import com.xeye.backend.apikey.infrastructure.web.dto.CreateApiKeyRequest;
import com.xeye.backend.apikey.infrastructure.web.dto.UpdateApiKeyRequest;
import com.xeye.backend.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api-keys")
public class ApiKeyController {

    private final ApiKeyUseCases apiKeys;

    public ApiKeyController(ApiKeyUseCases apiKeys) {
        this.apiKeys = apiKeys;
    }

    @GetMapping
    public List<ApiKeyResponse> list(@AuthenticationPrincipal AuthenticatedUser current) {
        return apiKeys.listForUser(current.id()).stream().map(ApiKeyResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiKeyResponse create(@AuthenticationPrincipal AuthenticatedUser current,
                                 @Valid @RequestBody CreateApiKeyRequest request) {
        return ApiKeyResponse.from(apiKeys.create(current.id(), request.name()));
    }

    @PutMapping("/{id}")
    public ApiKeyResponse rename(@AuthenticationPrincipal AuthenticatedUser current,
                                 @PathVariable Long id,
                                 @Valid @RequestBody UpdateApiKeyRequest request) {
        return ApiKeyResponse.from(apiKeys.rename(current.id(), id, request.name()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthenticatedUser current, @PathVariable Long id) {
        apiKeys.delete(current.id(), id);
    }
}
