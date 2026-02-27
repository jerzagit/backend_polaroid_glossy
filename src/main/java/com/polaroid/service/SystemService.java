package com.polaroid.service;

import com.polaroid.model.enums.PaymentStatus;
import com.polaroid.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SystemService {
    
    private final OrderRepository orderRepository;
    private final FileService fileService;
    
    @Value("${toyyibpay.fee-percentage:2.5}")
    private BigDecimal feePercentage;
    
    public Map<String, Object> getStorageInfo() {
        Map<String, Object> info = new HashMap<>();
        
        try {
            long fileCount = fileService.getStorageUsage();
            info.put("fileCount", fileCount);
            info.put("bucket", "polaroid-glossy");
            info.put("provider", "Supabase");
        } catch (Exception e) {
            log.error("Failed to get storage info: {}", e.getMessage());
            info.put("error", e.getMessage());
        }
        
        return info;
    }
    
    public Map<String, Object> getDatabaseInfo() {
        Map<String, Object> info = new HashMap<>();
        
        try {
            long userCount = orderRepository.count();
            info.put("status", "connected");
            info.put("provider", "Supabase PostgreSQL");
            info.put("orderCount", userCount);
        } catch (Exception e) {
            log.error("Failed to get database info: {}", e.getMessage());
            info.put("status", "error");
            info.put("error", e.getMessage());
        }
        
        return info;
    }
    
    public Map<String, Object> getPaymentCosts(LocalDateTime from, LocalDateTime to) {
        Map<String, Object> info = new HashMap<>();
        
        try {
            var paidOrders = orderRepository.findByPaymentStatusAndPaidAtBetween(
                    PaymentStatus.PAID, from, to);
            
            BigDecimal totalAmount = paidOrders.stream()
                    .map(order -> order.getTotal())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            BigDecimal totalFee = totalAmount.multiply(feePercentage).divide(new BigDecimal("100"));
            
            info.put("periodFrom", from.toString());
            info.put("periodTo", to.toString());
            info.put("totalTransactions", paidOrders.size());
            info.put("totalAmount", totalAmount);
            info.put("feePercentage", feePercentage);
            info.put("totalFee", totalFee);
            info.put("netAmount", totalAmount.subtract(totalFee));
            info.put("provider", "ToyyibPay");
        } catch (Exception e) {
            log.error("Failed to get payment costs: {}", e.getMessage());
            info.put("error", e.getMessage());
        }
        
        return info;
    }
    
    public Map<String, Object> getServerInfo() {
        Map<String, Object> info = new HashMap<>();
        
        Runtime runtime = Runtime.getRuntime();
        
        info.put("javaVersion", System.getProperty("java.version"));
        info.put("availableProcessors", runtime.availableProcessors());
        info.put("freeMemory", runtime.freeMemory());
        info.put("totalMemory", runtime.totalMemory());
        info.put("maxMemory", runtime.maxMemory());
        info.put("uptime", System.currentTimeMillis());
        
        return info;
    }
}
