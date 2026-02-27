package com.polaroid.service;

import com.polaroid.dto.request.LoginRequest;
import com.polaroid.dto.request.RegisterRequest;
import com.polaroid.dto.response.AuthResponse;
import com.polaroid.dto.response.UserResponse;
import com.polaroid.exception.BadRequestException;
import com.polaroid.model.User;
import com.polaroid.model.enums.Role;
import com.polaroid.repository.UserRepository;
import com.polaroid.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final AuthenticationManager authenticationManager;
    
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email already registered");
        }
        
        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .phone(request.getPhone())
                .role(Role.CUSTOMER)
                .affiliateCode(generateAffiliateCode())
                .isActive(true)
                .build();
        
        if (request.getAffiliateCode() != null && !request.getAffiliateCode().isEmpty()) {
            userRepository.findByAffiliateCode(request.getAffiliateCode())
                    .ifPresent(referrer -> user.setReferredBy(referrer.getId()));
        }
        
        User savedUser = userRepository.save(user);
        
        String token = tokenProvider.generateToken(savedUser.getEmail(), savedUser.getRole().name());
        String refreshToken = tokenProvider.generateRefreshToken(savedUser.getEmail());
        
        return AuthResponse.builder()
                .token(token)
                .refreshToken(refreshToken)
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole().name())
                .expiresIn(tokenProvider.getExpirationTime())
                .build();
    }
    
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );
        
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadRequestException("User not found"));
        
        if (!user.getIsActive()) {
            throw new BadRequestException("Account is disabled");
        }
        
        String token = tokenProvider.generateToken(user.getEmail(), user.getRole().name());
        String refreshToken = tokenProvider.generateRefreshToken(user.getEmail());
        
        return AuthResponse.builder()
                .token(token)
                .refreshToken(refreshToken)
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole().name())
                .expiresIn(tokenProvider.getExpirationTime())
                .build();
    }
    
    public UserResponse getCurrentUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("User not found"));
        
        return UserResponse.builder()
                .id(user.getId().toString())
                .email(user.getEmail())
                .name(user.getName())
                .phone(user.getPhone())
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole())
                .affiliateCode(user.getAffiliateCode())
                .isActive(user.getIsActive())
                .createdAt(user.getCreatedAt())
                .build();
    }
    
    public AuthResponse refreshToken(String refreshToken) {
        if (!tokenProvider.validateToken(refreshToken)) {
            throw new BadRequestException("Invalid refresh token");
        }
        
        String email = tokenProvider.getEmailFromToken(refreshToken);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("User not found"));
        
        String newToken = tokenProvider.generateToken(user.getEmail(), user.getRole().name());
        String newRefreshToken = tokenProvider.generateRefreshToken(user.getEmail());
        
        return AuthResponse.builder()
                .token(newToken)
                .refreshToken(newRefreshToken)
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole().name())
                .expiresIn(tokenProvider.getExpirationTime())
                .build();
    }
    
    private String generateAffiliateCode() {
        return "PG" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
    
    @Transactional
    public AuthResponse registerAsAdmin(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email already registered");
        }
        
        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .phone(request.getPhone())
                .role(Role.ADMIN)
                .affiliateCode(generateAffiliateCode())
                .isActive(true)
                .build();
        
        User savedUser = userRepository.save(user);
        
        String token = tokenProvider.generateToken(savedUser.getEmail(), savedUser.getRole().name());
        String refreshToken = tokenProvider.generateRefreshToken(savedUser.getEmail());
        
        return AuthResponse.builder()
                .token(token)
                .refreshToken(refreshToken)
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole().name())
                .expiresIn(tokenProvider.getExpirationTime())
                .build();
    }
}
