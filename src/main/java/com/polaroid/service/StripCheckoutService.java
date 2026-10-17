package com.polaroid.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaroid.dto.request.OrderRequest;
import com.polaroid.dto.response.OrderResponse;
import com.polaroid.dto.mapper.OrderMapper;
import com.polaroid.exception.BadRequestException;
import com.polaroid.exception.UploadConflictException;
import com.polaroid.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;

@Service
@RequiredArgsConstructor
public class StripCheckoutService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final OrderService orders;
    private final OrderRepository repository;
    private final OrderMapper mapper;
    @Value("${jwt.secret}") private String secret;

    @Transactional
    public OrderResponse create(OrderRequest request, String userEmail) {
        String key = request.getCheckoutKey();
        if (key == null || !key.matches("[a-fA-F0-9]{8}(-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}"))
            throw new BadRequestException("Strip checkout requires a UUID Idempotency-Key or checkoutKey");
        String scope = userEmail == null ? "guest:" + request.getCustomerEmail().toLowerCase(Locale.ROOT) : "user:" + userEmail.toLowerCase(Locale.ROOT);
        String requestKey = hash((scope + ":" + key).getBytes(StandardCharsets.UTF_8));
        String fingerprint;
        try { fingerprint = hash(json.writeValueAsBytes(request)); }
        catch (Exception e) { throw new BadRequestException("Invalid checkout request"); }
        // The unique claim plus row lock serializes identical concurrent attempts across app instances.
        jdbc.update("INSERT INTO strip_checkout_requests(request_key, request_hash) VALUES (?, ?) ON CONFLICT DO NOTHING", requestKey, fingerprint);
        var row = jdbc.queryForMap("SELECT request_hash, order_id, encrypted_upload_token FROM strip_checkout_requests WHERE request_key = ? FOR UPDATE", requestKey);
        if (!fingerprint.equals(row.get("request_hash"))) throw new UploadConflictException("Idempotency key was used with different order contents");
        if (row.get("order_id") != null) {
            var order = repository.findById(UUID.fromString(row.get("order_id").toString())).orElseThrow();
            var response = mapper.toDto(order);
            if (order.getUploadTokenHash() != null && row.get("encrypted_upload_token") != null)
                response.setUploadToken(decrypt(row.get("encrypted_upload_token").toString()));
            return response;
        }
        var response = orders.createOrder(request, userEmail);
        repository.flush();
        jdbc.update("UPDATE strip_checkout_requests SET order_id = ?, encrypted_upload_token = ? WHERE request_key = ?",
                UUID.fromString(response.getId()), encrypt(response.getUploadToken()), requestKey);
        return response;
    }
    private String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    private SecretKeySpec encryptionKey() { return new SecretKeySpec(HexFormat.of().parseHex(hash(secret.getBytes(StandardCharsets.UTF_8))), "AES"); }
    private String encrypt(String token) {
        if (token == null) return null;
        try {
            byte[] iv = new byte[12]; new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, encryptionKey(), new GCMParameterSpec(128, iv));
            return Base64.getEncoder().encodeToString(iv) + "." + Base64.getEncoder().encodeToString(cipher.doFinal(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException("Could not retain checkout upload authorization", e); }
    }
    private String decrypt(String encrypted) {
        try {
            String[] parts = encrypted.split("\\."); Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), new GCMParameterSpec(128, Base64.getDecoder().decode(parts[0])));
            return new String(cipher.doFinal(Base64.getDecoder().decode(parts[1])), StandardCharsets.UTF_8);
        } catch (Exception e) { throw new IllegalStateException("Could not recover checkout upload authorization", e); }
    }
}
