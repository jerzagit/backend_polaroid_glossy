package com.polaroid.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaroid.model.Order;
import com.polaroid.model.OrderItem;
import com.polaroid.repository.OrderItemRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;
    private final OrderItemRepository orderItemRepository;
    private final ObjectMapper objectMapper;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.email.from:no-reply@polaroidglossy.my}")
    private String appFromEmail;

    @Value("${app.support.email:admin@polaroidglossy.my}")
    private String supportEmail;

    @Value("${app.support.whatsapp:+60126620463}")
    private String supportWhatsApp;

    /** Comma-separated internal recipients for new-order alerts; blank disables them. */
    @Value("${app.notify.orders:}")
    private String orderNotifyEmails;

    @Value("${app.public-api-url:https://polaroid-glossy-backend.fly.dev}")
    private String publicApiUrl;

    @Value("${app.payment.expiration-hours:24}")
    private int expirationHours;

    @Value("${app.frontend-url:https://polaroidglossy.my}")
    private String frontendUrl;

    public void sendOrderConfirmation(Order order) {
        if (fromEmail == null || fromEmail.isBlank()) {
            log.warn("Mail not configured - skipping confirmation for order {} to {}", order.getOrderNumber(), order.getCustomerEmail());
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(appFromEmail);
            helper.setTo(order.getCustomerEmail());
            helper.setReplyTo(supportEmail);
            helper.setSubject("Order Confirmed / Pesanan Disahkan - " + order.getOrderNumber());

            Context ctx = new Context();
            ctx.setVariables(Map.of(
                "order", order,
                "trackingUrl", frontendUrl + "?order=" + order.getOrderNumber(),
                "supportWhatsApp", supportWhatsApp,
                "supportEmail", supportEmail
            ));

            String html = templateEngine.process("email/order-confirmation", ctx);
            helper.setText(html, true);

            mailSender.send(message);
            log.info("Order confirmation sent for {} to {}", order.getOrderNumber(), order.getCustomerEmail());
        } catch (MessagingException e) {
            log.error("Failed to send order confirmation for {}: {}", order.getOrderNumber(), e.getMessage());
        }
    }

    public void sendPaymentConfirmation(Order order) {
        if (fromEmail == null || fromEmail.isBlank()) {
            log.warn("Mail not configured - skipping payment confirmation for order {} to {}", order.getOrderNumber(), order.getCustomerEmail());
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(appFromEmail);
            helper.setTo(order.getCustomerEmail());
            helper.setReplyTo(supportEmail);
            helper.setSubject("Payment Received / Pembayaran Diterima - " + order.getOrderNumber());

            Context ctx = new Context();
            ctx.setVariables(Map.of(
                "order", order,
                "trackingUrl", frontendUrl + "?order=" + order.getOrderNumber(),
                "uploadUrl", frontendUrl + "?order=" + order.getOrderNumber() + "&upload=1",
                "hasMissingUploads", hasMissingUploads(order),
                "supportWhatsApp", supportWhatsApp,
                "supportEmail", supportEmail
            ));

            String html = templateEngine.process("email/payment-confirmation", ctx);
            helper.setText(html, true);

            mailSender.send(message);
            log.info("Payment confirmation sent for {} to {}", order.getOrderNumber(), order.getCustomerEmail());
        } catch (MessagingException e) {
            log.error("Failed to send payment confirmation for {}: {}", order.getOrderNumber(), e.getMessage());
        }
    }

    public boolean sendPaymentReminder(Order order) {
        if (fromEmail == null || fromEmail.isBlank()) {
            log.warn("Mail not configured - skipping reminder for order {} to {}", order.getOrderNumber(), order.getCustomerEmail());
            return false;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(appFromEmail);
            helper.setTo(order.getCustomerEmail());
            helper.setReplyTo(supportEmail);
            helper.setSubject("Payment Reminder / Peringatan Pembayaran - " + order.getOrderNumber());

            String deadline = order.getCreatedAt()
                    .plusHours(expirationHours)
                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a"));

            Context ctx = new Context();
            ctx.setVariables(Map.of(
                "order", order,
                "deadline", deadline,
                "trackingUrl", frontendUrl + "/profile/orders/" + order.getOrderNumber(),
                "supportWhatsApp", supportWhatsApp,
                "supportEmail", supportEmail
            ));

            String html = templateEngine.process("email/payment-reminder", ctx);
            helper.setText(html, true);

            mailSender.send(message);
            log.info("Payment reminder sent for order {} to {}", order.getOrderNumber(), order.getCustomerEmail());
            return true;
        } catch (Exception e) {
            log.error("Failed to send payment reminder for order {}: {}", order.getOrderNumber(), e.getMessage());
            return false;
        }
    }

    public boolean sendUploadReminder(Order order) {
        if (fromEmail == null || fromEmail.isBlank()) return false;
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(appFromEmail);
            helper.setTo(order.getCustomerEmail());
            helper.setReplyTo(supportEmail);
            helper.setSubject("Photos needed / Foto diperlukan - " + order.getOrderNumber());
            helper.setText("Your payment is received. Please upload the remaining photos before we can print your order. / Bayaran diterima. Sila muat naik baki foto sebelum pesanan dicetak.\n\n" + frontendUrl + "/profile/orders/" + order.getOrderNumber());
            mailSender.send(message);
            return true;
        } catch (Exception e) {
            log.error("Upload reminder failed for {}", order.getOrderNumber(), e);
            return false;
        }
    }

    /**
     * Internal alert so the shop notices a new order without opening the console.
     * Recipients come from app.notify.orders (comma-separated); blank disables it.
     */
    public void sendNewOrderAlert(Order order) {
        if (orderNotifyEmails == null || orderNotifyEmails.isBlank()) return;
        if (fromEmail == null || fromEmail.isBlank()) return;

        String[] recipients = java.util.Arrays.stream(orderNotifyEmails.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toArray(String[]::new);
        if (recipients.length == 0) return;

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(appFromEmail);
            helper.setTo(recipients);
            if (order.getCustomerEmail() != null && !order.getCustomerEmail().isBlank()) {
                helper.setReplyTo(order.getCustomerEmail());
            }
            helper.setSubject("Paid order / Pesanan dibayar - " + order.getOrderNumber());
            helper.setText(buildNewOrderBody(order), false);
            mailSender.send(message);
            log.info("New order alert sent for {} to {}", order.getOrderNumber(), String.join(",", recipients));
        } catch (Exception e) {
            log.error("New order alert failed for {}: {}", order.getOrderNumber(), e.getMessage());
        }
    }

    private String buildNewOrderBody(Order order) {
        StringBuilder body = new StringBuilder();
        body.append("New order received\n\n")
            .append("Order:    ").append(order.getOrderNumber()).append('\n')
            .append("Customer: ").append(text(order.getCustomerName())).append('\n')
            .append("Email:    ").append(text(order.getCustomerEmail())).append('\n')
            .append("Phone:    ").append(text(order.getCustomerPhone())).append('\n')
            .append("Ship to:  ").append(text(order.getCustomerHouseUnitNo())).append(", ")
            .append(text(order.getCustomerAddressLine1()))
            .append(order.getCustomerAddressLine2() == null || order.getCustomerAddressLine2().isBlank()
                    ? "" : ", " + order.getCustomerAddressLine2())
            .append('\n')
            .append("          ").append(text(order.getCustomerPostcode())).append(' ')
            .append(text(order.getCustomerCity())).append(", ")
            .append(text(order.getCustomerState())).append(", ")
            .append(text(order.getCustomerCountry())).append('\n')
            .append("Status:   ").append(order.getStatus())
            .append(" / payment ").append(order.getPaymentStatus()).append('\n')
            .append("Total:    ").append(money(order.getTotal())).append("\n\nItems:\n");

        if (order.getId() != null) {
            for (OrderItem item : orderItemRepository.findByOrderId(order.getId())) {
                body.append("  - ")
                    .append(item.getQuantity() == null ? 1 : item.getQuantity()).append(" x ")
                    .append(text(item.getPackageId() != null ? item.getPackageId() : item.getSizeId()));
                if (item.getVariantLabel() != null && !item.getVariantLabel().isBlank()) {
                    body.append(" (").append(item.getVariantLabel()).append(')');
                }
                body.append(" = ").append(money(item.getTotalPrice())).append('\n');
            }
        }

        body.append("\nAdmin console: ").append(publicApiUrl).append("/admin/orders\n");
        return body.toString();
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static String money(java.math.BigDecimal amount) {
        return amount == null ? "-" : "RM " + amount.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private boolean hasMissingUploads(Order order) {
        if (order.getId() == null) return false;
        List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
        if (items.isEmpty()) return false;
        int totalQuantity = items.stream().mapToInt(OrderItem::getQuantity).sum();
        int totalUploaded = items.stream()
            .mapToInt(item -> parseJsonStringList(item.getS3Keys()).size())
            .sum();
        return totalUploaded < totalQuantity;
    }

    private List<String> parseJsonStringList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse JSON list: {}", e.getMessage());
            return List.of();
        }
    }
}
