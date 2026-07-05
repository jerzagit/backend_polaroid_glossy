package com.polaroid.controller;

import com.polaroid.dto.request.LoginRequest;
import com.polaroid.dto.request.RegisterRequest;
import com.polaroid.dto.response.AuthResponse;
import com.polaroid.dto.response.UserResponse;
import com.polaroid.model.enums.Role;
import com.polaroid.repository.AddressRepository;
import com.polaroid.repository.OrderRepository;
import com.polaroid.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    
    private final AuthService authService;
    private final OrderRepository orderRepository;
    private final AddressRepository addressRepository;

    @Value("${app.setup-admin.enabled:false}")
    private boolean setupAdminEnabled;

    @Value("${app.setup-admin.secret:}")
    private String setupAdminSecret;
    
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }
    
    @PostMapping("/setup-admin")
    public ResponseEntity<AuthResponse> setupAdmin(@RequestParam String secret, @RequestBody RegisterRequest request) {
        if (!setupAdminEnabled) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        if (setupAdminSecret == null || setupAdminSecret.isBlank() || !setupAdminSecret.equals(secret)) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(authService.registerAsAdmin(request));
    }
    
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/google")
    public ResponseEntity<AuthResponse> google(@RequestBody Map<String, String> request) {
        return ResponseEntity.ok(authService.loginOrRegisterGoogleUser(
                request.get("email"),
                request.get("name"),
                request.get("avatarUrl")
        ));
    }
    
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestBody String refreshToken) {
        return ResponseEntity.ok(authService.refreshToken(refreshToken));
    }
    
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(Authentication authentication) {
        return ResponseEntity.ok(authService.getCurrentUser(authentication.getName()));
    }

    @GetMapping("/profile")
    public ResponseEntity<Map<String, Object>> getProfile(Authentication authentication) {
        UserResponse user = authService.getCurrentUser(authentication.getName());
        com.polaroid.model.User dbUser = authService.findByEmail(authentication.getName());
        long orderCount = orderRepository.countByUserId(dbUser.getId());
        long draftCount = orderRepository.countByUserIdAndStatus(dbUser.getId(), com.polaroid.model.enums.OrderStatus.DRAFT);
        long addressCount = addressRepository.countByUserId(dbUser.getId());
        return ResponseEntity.ok(Map.of(
                "success", true,
                "user", user,
                "orderCount", orderCount,
                "draftCount", draftCount,
                "addressCount", addressCount
        ));
    }
}
