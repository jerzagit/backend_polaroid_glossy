package com.polaroid.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaroid.exception.ResourceNotFoundException;
import com.polaroid.model.Order;
import com.polaroid.model.enums.PaymentStatus;
import com.polaroid.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.client.RestTemplate;

import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class PaymentService {
    
    private final OrderRepository orderRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
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
    private static final Pattern BILL_CODE_PATTERN = Pattern.compile("\"?BillCode\"?\\s*:\\s*\"?([^\"}\\],\\s]+)");
    
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
        params.add("billReturnUrl", buildReturnUrl(order.getOrderNumber()));
        params.add("billCallbackUrl", callbackUrl);
        params.add("billExternalReferenceNo", order.getOrderNumber());
        params.add("billTo", order.getCustomerName());
        params.add("billEmail", order.getCustomerEmail());
        params.add("billPhone", formatMalaysiaPhoneForPayment(order.getCustomerPhone()));
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

        String parsedBillCode = extractBillCodeFromJson(response);
        if (parsedBillCode != null && !parsedBillCode.isBlank()) {
            return parsedBillCode;
        }
        
        Matcher billCodeMatcher = BILL_CODE_PATTERN.matcher(response);
        if (billCodeMatcher.find()) {
            return billCodeMatcher.group(1).trim();
        }

        String rawBillCode = response.replace("\"", "").trim();
        if (!rawBillCode.matches("[A-Za-z0-9_-]{4,64}")) {
            throw new RuntimeException("Unexpected response from ToyyibPay: " + summarizeGatewayResponse(response));
        }

        return rawBillCode;
    }

    private String extractBillCodeFromJson(String response) {
        if (!response.startsWith("[") && !response.startsWith("{")) {
            return null;
        }

        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode payload = root.isArray() && !root.isEmpty() ? root.get(0) : root;
            JsonNode billCode = payload.get("BillCode");
            if (billCode != null && !billCode.asText().isBlank()) {
                return billCode.asText().trim();
            }

            JsonNode status = payload.get("status");
            JsonNode message = payload.has("msg") ? payload.get("msg") : payload.get("message");
            if (status != null || message != null) {
                String detail = message != null && !message.asText().isBlank()
                        ? message.asText()
                        : status.asText();
                throw new RuntimeException("ToyyibPay rejected payment bill: " + detail);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Unable to parse ToyyibPay response: " + summarizeGatewayResponse(response));
        }

        throw new RuntimeException("ToyyibPay response did not include a BillCode");
    }

    private String buildReturnUrl(String orderNumber) {
        return UriComponentsBuilder.fromUriString(returnUrl)
                .queryParam("order_id", orderNumber)
                .build()
                .toUriString();
    }

    private String summarizeGatewayResponse(String response) {
        return response.length() > 160 ? response.substring(0, 160) + "..." : response;
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
            case "0", "3", "failed", "fail" -> PaymentStatus.FAILED;
            default -> throw new IllegalArgumentException("Unknown ToyyibPay status");
        };
    }

    public String resolveOrderNumber(String orderReference, String billCode, String refno) {
        return resolveOrder(orderReference, billCode, refno).getOrderNumber();
    }

    public Map<String, String> resolvePaymentReturn(String orderReference, String billCode, String refno, String status) {
        Order order = resolveOrder(orderReference, billCode, refno);
        Map<String, String> result = new HashMap<>();
        result.put("orderNumber", order.getOrderNumber());
        result.put("paymentStatus", order.getPaymentStatus().name());
        result.put("orderStatus", order.getStatus().name());
        if (status != null && !status.isBlank()) {
            result.put("gatewayStatus", status);
        }
        return result;
    }

    private Order resolveOrder(String orderReference, String billCode, String refno) {
        return findOrderByOrderNumberOrBillCode(orderReference)
                .or(() -> findOrderByOrderNumberOrBillCode(billCode))
                .or(() -> findOrderByOrderNumberOrBillCode(refno))
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
    }

    private java.util.Optional<Order> findOrderByOrderNumberOrBillCode(String value) {
        if (value == null || value.isBlank()) {
            return java.util.Optional.empty();
        }

        String trimmed = value.trim();
        return orderRepository.findByOrderNumber(trimmed)
                .or(() -> orderRepository.findByToyyibpayRef(trimmed));
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

    private String formatMalaysiaPhoneForPayment(String phone) {
        if (phone == null || phone.isBlank()) {
            return "";
        }

        String digits = phone.replaceAll("\\D", "");
        if (digits.startsWith("60")) {
            return "+" + digits;
        }
        if (digits.startsWith("6")) {
            return "+60" + digits.substring(1);
        }
        return "+60" + digits;
    }

    private int parseAmountToCents(String amount) {
        BigDecimal parsed = new BigDecimal(amount.trim());
        if (parsed.scale() <= 0) {
            return parsed.intValueExact();
        }
        return toCents(parsed);
    }
}
