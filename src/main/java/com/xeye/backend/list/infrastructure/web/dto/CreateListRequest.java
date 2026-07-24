package com.xeye.backend.list.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateListRequest(
        @NotBlank @Size(max = 100) String name,
        String description,
        @JsonProperty("public") Boolean isPublic) {

    public boolean publicOrDefault() {
        return isPublic != null && isPublic;
    }
}
