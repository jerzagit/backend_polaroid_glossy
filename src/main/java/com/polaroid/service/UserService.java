package com.polaroid.service;

import com.polaroid.dto.mapper.UserMapper;
import com.polaroid.dto.response.UserResponse;
import com.polaroid.dto.request.UserRoleUpdateRequest;
import com.polaroid.exception.BadRequestException;
import com.polaroid.exception.ResourceNotFoundException;
import com.polaroid.model.User;
import com.polaroid.model.enums.Role;
import com.polaroid.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {
    
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    
    public Page<UserResponse> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable)
                .map(userMapper::toDto);
    }
    
    public Page<UserResponse> getUsersByRole(Role role, Pageable pageable) {
        return userRepository.findByRole(role, pageable)
                .map(userMapper::toDto);
    }
    
    public Page<UserResponse> searchUsers(String query, Pageable pageable) {
        return userRepository.findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(query, query, pageable)
                .map(userMapper::toDto);
    }
    
    public UserResponse getUserById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return userMapper.toDto(user);
    }
    
    public UserResponse getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return userMapper.toDto(user);
    }
    
    @Transactional
    public UserResponse updateUserRole(UUID id, UserRoleUpdateRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        user.setRole(request.getRole());
        user = userRepository.save(user);
        
        return userMapper.toDto(user);
    }
    
    @Transactional
    public UserResponse toggleUserActive(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        user.setIsActive(!user.getIsActive());
        user = userRepository.save(user);
        
        return userMapper.toDto(user);
    }
    
    @Transactional
    public String generateAffiliateCode(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        if (user.getAffiliateCode() != null && !user.getAffiliateCode().isEmpty()) {
            throw new BadRequestException("User already has an affiliate code");
        }
        
        String affiliateCode = "PG" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        user.setAffiliateCode(affiliateCode);
        userRepository.save(user);
        
        return affiliateCode;
    }
    
    public long countUsersByRole(Role role) {
        return userRepository.countByRole(role);
    }
}
