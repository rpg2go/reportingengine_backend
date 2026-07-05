package com.reporting.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.lang.NonNull;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that propagates a {@code X-Correlation-ID} header through the
 * request/response cycle and attaches it to the SLF4J MDC so every log line
 * emitted during the request automatically includes the ID.
 *
 * <h2>Behaviour</h2>
 * <ol>
 *   <li>If the incoming request carries an {@code X-Correlation-ID} header its
 *       value is reused, allowing end-to-end tracing across the frontend,
 *       gateway, and this service.</li>
 *   <li>Otherwise a new UUID v4 is generated for this request.</li>
 *   <li>The resolved ID is echoed back on the response via the same header so
 *       callers can correlate their own logs.</li>
 *   <li>The MDC entry is always cleared after the request completes to prevent
 *       leakage into other requests on the same thread (especially important
 *       with virtual threads / thread pools).</li>
 * </ol>
 *
 * <h2>Log pattern</h2>
 * The {@code correlationId} MDC key is referenced in {@code application.properties}:
 * <pre>{@code
 * logging.pattern.console=... [%X{correlationId:-no-corr-id}] ...
 * }</pre>
 *
 * @see org.springframework.web.filter.OncePerRequestFilter
 * @since 1.1.0
 */
@Component
@Order(1)
public class CorrelationIdFilter extends OncePerRequestFilter {

    /** HTTP header name used to carry the correlation identifier. */
    public static final String HEADER_NAME = "X-Correlation-ID";

    /** MDC key under which the correlation ID is stored. */
    public static final String MDC_KEY = "correlationId";

    /**
     * {@inheritDoc}
     *
     * <p>Reads or generates a correlation ID, stores it in MDC, passes it on
     * the response header, then delegates to the filter chain.</p>
     */
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String correlationId = request.getHeader(HEADER_NAME);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER_NAME, correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
