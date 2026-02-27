package com.polaroid.controller;

import com.polaroid.dto.request.LoginRequest;
import com.polaroid.dto.request.RegisterRequest;
import com.polaroid.dto.response.AuthResponse;
import com.polaroid.dto.response.UserResponse;
import com.polaroid.model.enums.Role;
import com.polaroid.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    
    private final AuthService authService;
    
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }
    
    @PostMapping("/setup-admin")
    public ResponseEntity<AuthResponse> setupAdmin(@RequestParam String secret, @RequestBody RegisterRequest request) {
        if (!"admin-secret-2024".equals(secret)) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(authService.registerAsAdmin(request));
    }
    
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
    
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestBody String refreshToken) {
        return ResponseEntity.ok(authService.refreshToken(refreshToken));
    }
    
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(Authentication authentication) {
        return ResponseEntity.ok(authService.getCurrentUser(authentication.getName()));
    }
}
