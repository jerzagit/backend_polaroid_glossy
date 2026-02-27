package com.polaroid.controller;

import com.polaroid.dto.response.OrderResponse;
import com.polaroid.dto.response.StatsOverviewResponse;
import com.polaroid.dto.response.UserResponse;
import com.polaroid.model.enums.OrderStatus;
import com.polaroid.model.enums.PaymentStatus;
import com.polaroid.model.enums.Role;
import com.polaroid.service.AuthService;
import com.polaroid.service.OrderService;
import com.polaroid.service.StatsService;
import com.polaroid.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.polaroid.config.DateTimeConfig.MALAYSIA_DATE_TIME_FORMATTER;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class WebController {
    
    private final AuthService authService;
    private final OrderService orderService;
    private final StatsService statsService;
    private final UserService userService;
    
    @GetMapping("/login")
    public String loginPage() {
        return "admin/login";
    }
    
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MARKETING', 'PACKER')")
    public String dashboard(Model model, Authentication authentication) {
        UserResponse user = authService.getCurrentUser(authentication.getName());
        StatsOverviewResponse stats = statsService.getOverview();
        Map<String, Long> statusCounts = statsService.getOrdersByStatusMap();
        
        model.addAttribute("user", user);
        model.addAttribute("stats", stats);
        model.addAttribute("statusCounts", statusCounts);
        
        return "admin/dashboard";
    }
    
    @GetMapping("/orders")
    @PreAuthorize("hasAnyRole('ADMIN', 'MARKETING', 'PACKER')")
    public String orders(
            Model model,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        
        UserResponse user = authService.getCurrentUser(authentication.getName());
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        
        Page<OrderResponse> ordersPage;
        if (search != null && !search.isEmpty()) {
            ordersPage = orderService.getOrdersWithFilters(status, paymentStatus, null, null, null, pageable);
        } else {
            ordersPage = orderService.getOrdersWithFilters(status, paymentStatus, null, null, null, pageable);
        }
        
        model.addAttribute("user", user);
        model.addAttribute("orders", ordersPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", ordersPage.getTotalPages());
        model.addAttribute("status", status);
        model.addAttribute("paymentStatus", paymentStatus);
        model.addAttribute("search", search);
        
        return "admin/orders/list";
    }
    
    @GetMapping("/orders/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MARKETING', 'PACKER')")
    public String orderDetail(@PathVariable UUID id, Model model, Authentication authentication) {
        UserResponse user = authService.getCurrentUser(authentication.getName());
        OrderResponse order = orderService.getOrderByNumber(id.toString());
        
        model.addAttribute("user", user);
        model.addAttribute("order", order);
        model.addAttribute("formatter", MALAYSIA_DATE_TIME_FORMATTER);
        
        return "admin/orders/detail";
    }
    
    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public String users(
            Model model,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        
        UserResponse currentUser = authService.getCurrentUser(authentication.getName());
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        
        Page<UserResponse> usersPage;
        if (search != null && !search.isEmpty()) {
            usersPage = userService.searchUsers(search, pageable);
        } else if (role != null) {
            usersPage = userService.getUsersByRole(role, pageable);
        } else {
            usersPage = userService.getAllUsers(pageable);
        }
        
        model.addAttribute("user", currentUser);
        model.addAttribute("users", usersPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", usersPage.getTotalPages());
        model.addAttribute("role", role);
        model.addAttribute("search", search);
        
        return "admin/users";
    }
    
    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'MARKETING')")
    public String stats(Model model, Authentication authentication) {
        UserResponse user = authService.getCurrentUser(authentication.getName());
        StatsOverviewResponse stats = statsService.getOverview();
        Map<String, Long> statusCounts = statsService.getOrdersByStatusMap();
        List<Object[]> stateCounts = statsService.getOrdersByState();
        List<Object[]> topSizes = statsService.getTopSellingSizes();
        
        model.addAttribute("user", user);
        model.addAttribute("stats", stats);
        model.addAttribute("statusCounts", statusCounts);
        model.addAttribute("stateCounts", stateCounts);
        model.addAttribute("topSizes", topSizes);
        
        return "admin/stats";
    }
    
    @GetMapping("/settings")
    @PreAuthorize("hasRole('ADMIN')")
    public String settings(Model model, Authentication authentication) {
        UserResponse user = authService.getCurrentUser(authentication.getName());
        
        model.addAttribute("user", user);
        
        return "admin/settings";
    }
}
