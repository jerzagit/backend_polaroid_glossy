package com.polaroid.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class DraftCleanupService {

    private final OrderService orderService;

    @Scheduled(fixedRate = 600_000)
    public void cleanupExpiredDrafts() {
        log.debug("Running draft cleanup check...");
        orderService.expireDraftOrders();
    }
}
