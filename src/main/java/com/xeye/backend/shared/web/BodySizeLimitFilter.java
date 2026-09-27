package com.xeye.backend.shared.web;

import com.xeye.backend.shared.exception.PayloadTooLargeException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;

/**
 * Límite de tamaño del cuerpo por endpoint, antes de que nadie lo parsee. Con
 * {@code Content-Length} se rechaza al instante con 413; sin él (chunked) se cuenta lo leído y
 * se corta al pasarse (el parser de JSON lo ve como {@link PayloadTooLargeException} y
 * {@code GlobalExceptionHandler} lo convierte en 413). Tres límites: el general (1 MB), el de
 * la importación masiva y el del webhook del worker, cuyo body son los embeddings de la lista
 * entera en base64 (decenas de MB con listas grandes).
 */
public class BodySizeLimitFilter extends OncePerRequestFilter {

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    /** Una regla: prefijo/patrón de ruta (Ant) y su tope en bytes. */
    public record Rule(String pathPattern, long maxBytes) {
        boolean matches(String path) {
            if (pathPattern.endsWith("/**")) {
                return path.startsWith(pathPattern.substring(0, pathPattern.length() - 3));
            }
            return MATCHER.match(pathPattern, path);
        }
    }

    private final List<Rule> rules;
    private final long defaultMaxBytes;
    private final ObjectMapper objectMapper;

    public BodySizeLimitFilter(List<Rule> rules, long defaultMaxBytes, ObjectMapper objectMapper) {
        this.rules = rules;
        this.defaultMaxBytes = defaultMaxBytes;
        this.objectMapper = objectMapper;
    }

    long limitFor(String path) {
        for (Rule rule : rules) {
            if (rule.matches(path)) {
                return rule.maxBytes();
            }
        }
        return defaultMaxBytes;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long limit = limitFor(request.getRequestURI());
        long declared = request.getContentLengthLong();
        if (declared > limit) {
            reject(response, limit);
            return;
        }
        chain.doFilter(declared >= 0 ? request : new CountingRequest(request, limit), response);
    }

    private void reject(HttpServletResponse response, long limit) throws IOException {
        response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiError body = ApiError.of(HttpStatus.PAYLOAD_TOO_LARGE.value(),
                HttpStatus.PAYLOAD_TOO_LARGE.getReasonPhrase(),
                "Request body exceeds the limit of " + limit + " bytes", "REQUEST_TOO_LARGE");
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    /** Envuelve el input stream para cortar un cuerpo chunked que supere el límite. */
    private static final class CountingRequest extends HttpServletRequestWrapper {

        private final long limit;

        CountingRequest(HttpServletRequest request, long limit) {
            super(request);
            this.limit = limit;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            ServletInputStream delegate = super.getInputStream();
            return new ServletInputStream() {
                private long read;

                private void count(long n) {
                    if (n > 0) {
                        read += n;
                        if (read > limit) {
                            throw new PayloadTooLargeException(
                                    "Request body exceeds the limit of " + limit + " bytes");
                        }
                    }
                }

                @Override
                public int read() throws IOException {
                    int b = delegate.read();
                    count(b >= 0 ? 1 : 0);
                    return b;
                }

                @Override
                public int read(byte[] buffer, int off, int len) throws IOException {
                    int n = delegate.read(buffer, off, len);
                    count(n);
                    return n;
                }

                @Override
                public boolean isFinished() {
                    return delegate.isFinished();
                }

                @Override
                public boolean isReady() {
                    return delegate.isReady();
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    delegate.setReadListener(listener);
                }
            };
        }
    }
}
