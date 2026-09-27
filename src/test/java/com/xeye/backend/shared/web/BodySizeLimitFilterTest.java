package com.xeye.backend.shared.web;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BodySizeLimitFilterTest {

    private final BodySizeLimitFilter filter = new BodySizeLimitFilter(List.of(
            new BodySizeLimitFilter.Rule("/webhooks/**", 100_000),
            new BodySizeLimitFilter.Rule("/internal/**", 8_000),
            new BodySizeLimitFilter.Rule("/lists/*/elements/import", 16_000)),
            1_000, new ObjectMapper());

    @Test
    void picksTheMostSpecificRuleOrTheDefault() {
        assertEquals(100_000, filter.limitFor("/webhooks/training-update"));
        assertEquals(8_000, filter.limitFor("/internal/search/logs"));
        assertEquals(16_000, filter.limitFor("/lists/42/elements/import"));
        assertEquals(1_000, filter.limitFor("/lists/42/elements"));
        assertEquals(1_000, filter.limitFor("/auth/login"));
    }
}
