package com.polaroid.service;

import com.polaroid.model.Order;
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
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.email.from:no-reply@polaroidglossy.com}")
    private String appFromEmail;

    @Value("${app.support.email:payment@polaroidglossy.my}")
    private String supportEmail;

    @Value("${app.support.whatsapp:+60126620463}")
    private String supportWhatsApp;

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
            helper.setSubject("Payment Received / Pembayaran Diterima - " + order.getOrderNumber());

            Context ctx = new Context();
            ctx.setVariables(Map.of(
                "order", order,
                "trackingUrl", frontendUrl + "?order=" + order.getOrderNumber(),
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

    public void sendPaymentReminder(Order order) {
        if (fromEmail == null || fromEmail.isBlank()) {
            log.warn("Mail not configured - skipping reminder for order {} to {}", order.getOrderNumber(), order.getCustomerEmail());
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(appFromEmail);
            helper.setTo(order.getCustomerEmail());
            helper.setSubject("Payment Reminder / Peringatan Pembayaran - " + order.getOrderNumber());

            String deadline = order.getCreatedAt()
                    .plusHours(expirationHours)
                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a"));

            Context ctx = new Context();
            ctx.setVariables(Map.of(
                "order", order,
                "deadline", deadline,
                "trackingUrl", frontendUrl + "?order=" + order.getOrderNumber(),
                "supportWhatsApp", supportWhatsApp,
                "supportEmail", supportEmail
            ));

            String html = templateEngine.process("email/payment-reminder", ctx);
            helper.setText(html, true);

            mailSender.send(message);
            log.info("Payment reminder sent for order {} to {}", order.getOrderNumber(), order.getCustomerEmail());
        } catch (MessagingException e) {
            log.error("Failed to send payment reminder for order {}: {}", order.getOrderNumber(), e.getMessage());
        }
    }
}
