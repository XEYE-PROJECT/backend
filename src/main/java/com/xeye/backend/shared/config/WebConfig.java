package com.xeye.backend.shared.config;

import com.xeye.backend.shared.web.BodySizeLimitFilter;
import com.xeye.backend.shared.web.RequestIdFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/** Filtros servlet de alcance global (antes de Spring Security). */
@Configuration
public class WebConfig {

    /** El primero de todos: cualquier log o respuesta posterior (413 incluido) ya lleva el request id. */
    @Bean
    public FilterRegistrationBean<RequestIdFilter> requestIdFilter() {
        FilterRegistrationBean<RequestIdFilter> registration = new FilterRegistrationBean<>(new RequestIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }

    @Bean
    public FilterRegistrationBean<BodySizeLimitFilter> bodySizeLimitFilter(HttpLimitsProperties limits,
                                                                           ObjectMapper objectMapper) {
        BodySizeLimitFilter filter = new BodySizeLimitFilter(List.of(
                new BodySizeLimitFilter.Rule("/webhooks/**", limits.webhookMaxBodyBytes()),
                new BodySizeLimitFilter.Rule("/internal/**", limits.internalMaxBodyBytes()),
                new BodySizeLimitFilter.Rule("/lists/*/elements/import", limits.importMaxBodyBytes())),
                limits.maxBodyBytes(), objectMapper);
        FilterRegistrationBean<BodySizeLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
