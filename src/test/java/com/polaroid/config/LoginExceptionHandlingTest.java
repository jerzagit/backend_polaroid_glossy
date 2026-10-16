package com.polaroid.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LoginExceptionHandlingTest {
    private MockMvc mvc(RuntimeException failure) {
        return MockMvcBuilders.standaloneSetup(new LoginController(failure))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void incorrectCredentialsReturnUnauthorized() throws Exception {
        mvc(new BadCredentialsException("private details"))
                .perform(post("/auth/login"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void disabledAccountReturnsUnauthorized() throws Exception {
        mvc(new DisabledException("private details"))
                .perform(post("/auth/login"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Account is unavailable. Contact an administrator."));
    }

    @Test
    void infrastructureFailuresRemainServerErrors() throws Exception {
        mvc(new InternalAuthenticationServiceException("database failure"))
                .perform(post("/auth/login"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Internal server error"));
    }

    @RestController
    static class LoginController {
        private final RuntimeException failure;
        LoginController(RuntimeException failure) { this.failure = failure; }
        @PostMapping("/auth/login")
        public void login() { throw failure; }
    }
}
