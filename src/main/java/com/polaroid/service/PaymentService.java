package com.polaroid.service;

import com.polaroid.exception.ResourceNotFoundException;
import com.polaroid.model.Order;
import com.polaroid.model.enums.PaymentStatus;
import com.polaroid.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PaymentService {
    
    private final OrderRepository orderRepository;
    private final RestTemplate restTemplate;
    
    @Value("${toyyibpay.secret-key}")
    private String toyyibpaySecretKey;
    
    @Value("${toyyibpay.category-code}")
    private String categoryCode;
    
    @Value("${toyyibpay.return-url}")
    private String returnUrl;
    
    @Value("${toyyibpay.callback-url}")
    private String callbackUrl;

    @Value("${toyyibpay.verify-callback:true}")
    private boolean verifyCallback;
    
    private static final String TOYYIBPAY_API_URL = "https://toyyibpay.com/index.php/api/createBill";
    
    private final OrderService orderService;

    public Map<String, String> createPayment(String orderNumber, String userEmail) {
        Order order = orderService.getAuthorizedOrder(orderNumber, userEmail);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("userSecretKey", toyyibpaySecretKey);
        params.add("categoryCode", categoryCode);
        params.add("billName", truncate(order.getOrderNumber(), 30));
        params.add("billDescription", "Polaroid Glossy - " + order.getOrderNumber());
        params.add("billPriceSetting", "1");
        params.add("billPayorInfo", "1");
        params.add("billAmount", String.valueOf(toCents(order.getTotal())));
        params.add("billReturnUrl", returnUrl + "?order_id=" + order.getOrderNumber());
        params.add("billCallbackUrl", callbackUrl);
        params.add("billExternalReferenceNo", order.getOrderNumber());
        params.add("billTo", order.getCustomerName());
        params.add("billEmail", order.getCustomerEmail());
        params.add("billPhone", order.getCustomerPhone() != null ? order.getCustomerPhone() : "");
        params.add("billPaymentChannel", "0");
        params.add("billChargeToCustomer", "1");
        
        try {
            String response = restTemplate.postForObject(TOYYIBPAY_API_URL, params, String.class);
            
            String billCode = extractBillCode(response);
            
            order.setToyyibpayRef(billCode);
            orderRepository.save(order);
            
            Map<String, String> result = new HashMap<>();
            result.put("billCode", billCode);
            result.put("paymentUrl", "https://toyyibpay.com/" + billCode);
            result.put("orderNumber", orderNumber);
            
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create payment: " + e.getMessage());
        }
    }
    
    private String extractBillCode(String response) {
        if (response == null || response.isEmpty()) {
            throw new RuntimeException("Empty response from ToyyibPay");
        }
        
        response = response.trim();
        
        if (response.startsWith("[")) {
            response = response.substring(1);
        }
        if (response.endsWith("]")) {
            response = response.substring(0, response.length() - 1);
        }
        
        if (response.contains("BillCode")) {
            int start = response.indexOf("BillCode") + 10;
            int end = response.indexOf("\"", start);
            if (end > start) {
                return response.substring(start, end);
            }
        }
        
        return response.replace("\"", "").trim();
    }
    
    private String truncate(String str, int maxLength) {
        if (str == null) return "";
        return str.length() > maxLength ? str.substring(0, maxLength) : str;
    }

    public PaymentStatus verifyCallback(String orderNumber, String refno, String billCode, String status, String amount, String hash) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderNumber));

        if (verifyCallback) {
            if (refno == null || refno.isBlank()) {
                throw new SecurityException("ToyyibPay payment reference is missing");
            }
            if (billCode == null || billCode.isBlank() || !billCode.equals(order.getToyyibpayRef())) {
                throw new SecurityException("ToyyibPay bill code mismatch");
            }
            if (amount == null || parseAmountToCents(amount) != toCents(order.getTotal())) {
                throw new SecurityException("ToyyibPay amount mismatch");
            }
            if (hash == null || hash.isBlank() || !hash.equalsIgnoreCase(callbackHash(status, orderNumber, refno))) {
                throw new SecurityException("ToyyibPay callback hash mismatch");
            }
        }

        String normalizedStatus = status == null ? "" : status.trim().toLowerCase();
        return switch (normalizedStatus) {
            case "1", "success", "paid" -> PaymentStatus.PAID;
            case "2", "pending" -> PaymentStatus.PENDING;
            case "3", "failed", "fail" -> PaymentStatus.FAILED;
            default -> throw new IllegalArgumentException("Unknown ToyyibPay status");
        };
    }

    private String callbackHash(String status, String orderNumber, String refno) {
        return md5(toyyibpaySecretKey + status + orderNumber + refno + "ok");
    }

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to verify ToyyibPay callback", e);
        }
    }

    private int toCents(BigDecimal amount) {
        return amount.multiply(new BigDecimal("100")).setScale(0, RoundingMode.HALF_UP).intValueExact();
    }

    private int parseAmountToCents(String amount) {
        BigDecimal parsed = new BigDecimal(amount.trim());
        if (parsed.scale() <= 0) {
            return parsed.intValueExact();
        }
        return toCents(parsed);
    }
}
