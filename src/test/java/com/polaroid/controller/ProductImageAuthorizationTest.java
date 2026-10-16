package com.polaroid.controller;

import com.polaroid.dto.request.admin.ProductImagesRequest;
import com.polaroid.service.ProductImageService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductImageAuthorizationTest {
    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean ProductImageService service() { return mock(ProductImageService.class); }
        @Bean ProductImageController controller(ProductImageService service) { return new ProductImageController(service); }
    }

    @Test
    void onlyAdminCanChangeProductImages() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var controller = context.getBean(ProductImageController.class);
            var request = new ProductImagesRequest();
            request.setImages(List.of());
            for (String role : List.of("MARKETING", "PACKER", "CUSTOMER")) {
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("user", "", AuthorityUtils.createAuthorityList("ROLE_" + role)));
                assertThrows(AccessDeniedException.class, () -> controller.update(1L, request));
                assertThrows(AccessDeniedException.class, () -> controller.upload(1L, null));
            }
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin", "", AuthorityUtils.createAuthorityList("ROLE_ADMIN")));
            controller.update(1L, request);
            controller.upload(1L, null);
            var service = context.getBean(ProductImageService.class);
            verify(service).updateImages(1L, List.of());
            verify(service).upload(1L, null);
        } finally { SecurityContextHolder.clearContext(); }
    }
}
