package com.xeye.backend.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Primer filtro de la cadena (ver {@code WebConfig}): fija el {@code X-Request-Id} de la
 * petición en el MDC y en la respuesta antes de que nada más (límite de tamaño, seguridad,
 * controladores) escriba un log o una respuesta. Se limpia siempre al terminar: los hilos de
 * Tomcat se reutilizan.
 */
public class RequestIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id = RequestId.sanitize(request.getHeader(RequestId.HEADER));
        if (id == null) {
            id = RequestId.newId();
        }
        MDC.put(RequestId.MDC_KEY, id);
        response.setHeader(RequestId.HEADER, id);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(RequestId.MDC_KEY);
        }
    }
}
