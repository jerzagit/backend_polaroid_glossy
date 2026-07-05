package com.polaroid.controller;

import com.polaroid.dto.request.AddressRequest;
import com.polaroid.dto.response.AddressResponse;
import com.polaroid.service.AddressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.polaroid.model.User;
import com.polaroid.repository.UserRepository;
import com.polaroid.exception.ResourceNotFoundException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAddresses(Authentication authentication) {
        UUID userId = getUserId(authentication);
        List<AddressResponse> addresses = addressService.getAddresses(userId);
        return ResponseEntity.ok(Map.of("success", true, "addresses", addresses));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createAddress(
            @Valid @RequestBody AddressRequest request,
            Authentication authentication) {
        UUID userId = getUserId(authentication);
        AddressResponse address = addressService.createAddress(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("success", true, "address", address));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateAddress(
            @PathVariable UUID id,
            @Valid @RequestBody AddressRequest request,
            Authentication authentication) {
        UUID userId = getUserId(authentication);
        AddressResponse address = addressService.updateAddress(id, userId, request);
        return ResponseEntity.ok(Map.of("success", true, "address", address));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteAddress(
            @PathVariable UUID id,
            Authentication authentication) {
        UUID userId = getUserId(authentication);
        addressService.deleteAddress(id, userId);
        return ResponseEntity.ok(Map.of("success", true, "message", "Address deleted"));
    }

    @PatchMapping("/{id}/default")
    public ResponseEntity<Map<String, Object>> setDefault(
            @PathVariable UUID id,
            Authentication authentication) {
        UUID userId = getUserId(authentication);
        AddressResponse address = addressService.setDefault(id, userId);
        return ResponseEntity.ok(Map.of("success", true, "address", address));
    }

    private UUID getUserId(Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return user.getId();
    }
}
