package com.polaroid.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;

class ApiAuthenticationEntryPointTest {

    private final ApiAuthenticationEntryPoint entryPoint =
            new ApiAuthenticationEntryPoint(new ObjectMapper());

    @Test
    void apiRequestsGetJsonUnauthorizedInsteadOfFormLoginRedirect() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new BadCredentialsException("missing token"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getRedirectedUrl()).isNull();
        assertThat(response.getContentAsString())
                .contains("\"success\":false")
                .contains("Authentication required");
    }

    @Test
    void apiRequestsUnderAContextPathAreStillRecognised() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/backend/api/orders/my");
        request.setContextPath("/backend");
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new BadCredentialsException("missing token"));

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void adminConsolePagesStillRedirectToTheLoginPage() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new BadCredentialsException("missing session"));

        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getRedirectedUrl()).endsWith("/admin/login");
    }
}
