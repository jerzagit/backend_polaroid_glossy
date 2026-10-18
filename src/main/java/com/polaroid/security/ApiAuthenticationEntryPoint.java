package com.polaroid.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

/**
 * Splits unauthenticated requests by audience.
 *
 * <p>The JSON API answers {@code 401} with a body; without this, Spring's form-login
 * entry point redirects every unauthenticated call to {@code /admin/login}. A browser
 * XHR cannot use that redirect (the location is built from the internal http scheme and
 * the target is HTML), so the frontend only sees an opaque "network error" instead of
 * an expired session. Admin console pages keep the redirect so form login still works.
 */
@Component
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final LoginUrlAuthenticationEntryPoint loginEntryPoint =
            new LoginUrlAuthenticationEntryPoint("/admin/login");
    private final ObjectMapper objectMapper;

    public ApiAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException, jakarta.servlet.ServletException {
        if (isApiRequest(request)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getWriter(), Map.of(
                    "success", false,
                    "message", "Authentication required",
                    "error", "Authentication required"
            ));
            return;
        }
        loginEntryPoint.commence(request, response, authException);
    }

    static boolean isApiRequest(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        return path.startsWith("/api/");
    }
}
