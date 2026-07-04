package com.polaroid.service;

import com.polaroid.model.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

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

    public void sendOrderConfirmation(Order order) {
        if (fromEmail == null || fromEmail.isBlank()) {
            log.warn("Mail not configured - skipping confirmation for order {} to {}", order.getOrderNumber(), order.getCustomerEmail());
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(appFromEmail);
            message.setTo(order.getCustomerEmail());
            message.setSubject("Order Confirmed / Pesanan Disahkan - " + order.getOrderNumber());

            String total = "RM" + String.format("%.2f", order.getTotal());

            message.setText(
                "Dear " + order.getCustomerName() + ",\n\n"
                + "Thank you for your order! Your order has been received and is being processed.\n\n"
                + "Order Number: " + order.getOrderNumber() + "\n"
                + "Total: " + total + "\n"
                + "Payment Method: " + (order.getPaymentMethod() != null ? order.getPaymentMethod() : "Bank Transfer") + "\n\n"
                + "You will receive another email once payment is confirmed.\n\n"
                + "---\n\n"
                + "Yth. " + order.getCustomerName() + ",\n\n"
                + "Terima kasih atas pesanan anda! Pesanan anda telah diterima dan sedang diproses.\n\n"
                + "Nombor Pesanan: " + order.getOrderNumber() + "\n"
                + "Jumlah: " + total + "\n"
                + "Kaedah Pembayaran: " + (order.getPaymentMethod() != null ? order.getPaymentMethod() : "Pemindahan Bank") + "\n\n"
                + "Anda akan menerima e-mel selepas pembayaran disahkan.\n\n"
                + "Terima kasih,\n"
                + "Polaroid Glossy MY"
            );

            mailSender.send(message);
            log.info("Order confirmation sent for {} to {}", order.getOrderNumber(), order.getCustomerEmail());
        } catch (Exception e) {
            log.error("Failed to send order confirmation for {}: {}", order.getOrderNumber(), e.getMessage());
        }
    }

    public void sendPaymentConfirmation(Order order) {
        if (fromEmail == null || fromEmail.isBlank()) {
            log.warn("Mail not configured - skipping payment confirmation for order {} to {}", order.getOrderNumber(), order.getCustomerEmail());
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(appFromEmail);
            message.setTo(order.getCustomerEmail());
            message.setSubject("Payment Received / Pembayaran Diterima - " + order.getOrderNumber());

            String total = "RM" + String.format("%.2f", order.getTotal());

            message.setText(
                "Dear " + order.getCustomerName() + ",\n\n"
                + "Your payment has been received! We are now processing your order.\n\n"
                + "Order Number: " + order.getOrderNumber() + "\n"
                + "Amount Paid: " + total + "\n"
                + "Shipping to: " + order.getCustomerAddressLine1() + ", " + order.getCustomerCity() + "\n\n"
                + "You will receive a shipping notification once your order is on its way.\n\n"
                + "---\n\n"
                + "Yth. " + order.getCustomerName() + ",\n\n"
                + "Pembayaran anda telah diterima! Kami sedang memproses pesanan anda.\n\n"
                + "Nombor Pesanan: " + order.getOrderNumber() + "\n"
                + "Jumlah Dibayar: " + total + "\n"
                + "Dihantar ke: " + order.getCustomerAddressLine1() + ", " + order.getCustomerCity() + "\n\n"
                + "Anda akan menerima notifikasi penghantaran selepas pesanan dihantar.\n\n"
                + "Terima kasih,\n"
                + "Polaroid Glossy MY"
            );

            mailSender.send(message);
            log.info("Payment confirmation sent for {} to {}", order.getOrderNumber(), order.getCustomerEmail());
        } catch (Exception e) {
            log.error("Failed to send payment confirmation for {}: {}", order.getOrderNumber(), e.getMessage());
        }
    }

    public void sendPaymentReminder(Order order) {
        if (fromEmail == null || fromEmail.isBlank()) {
            log.warn("Mail not configured - skipping reminder for order {} to {}", order.getOrderNumber(), order.getCustomerEmail());
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(appFromEmail);
            message.setTo(order.getCustomerEmail());
            message.setSubject("Payment Reminder / Peringatan Pembayaran - " + order.getOrderNumber());

            String deadline = order.getCreatedAt()
                    .plusHours(expirationHours)
                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a"));

            message.setText(
                "Dear " + order.getCustomerName() + ",\n\n"
                + "This is a reminder that your payment for order " + order.getOrderNumber() + " is still pending.\n\n"
                + "Please make your payment before " + deadline + " to avoid automatic cancellation.\n\n"
                + "Bank: Maybank\n"
                + "Account Name: Acachiaa Empire\n"
                + "Account Number: 123456789012\n"
                + "Amount: RM" + String.format("%.2f", order.getTotal()) + "\n"
                + "Reference: " + order.getOrderNumber() + "\n\n"
                + "After payment, send your receipt to:\n"
                + "  WhatsApp: " + supportWhatsApp + "\n"
                + "  Email: " + supportEmail + "\n\n"
                + "---\n\n"
                + "Yth. " + order.getCustomerName() + ",\n\n"
                + "Ini adalah peringatan bahawa pembayaran untuk pesanan " + order.getOrderNumber() + " masih belum diterima.\n\n"
                + "Sila buat pembayaran sebelum " + deadline + " untuk mengelakkan pembatalan automatik.\n\n"
                + "Bank: Maybank\n"
                + "Nama Akaun: Acachiaa Empire\n"
                + "Nombor Akaun: 123456789012\n"
                + "Jumlah: RM" + String.format("%.2f", order.getTotal()) + "\n"
                + "Rujukan: " + order.getOrderNumber() + "\n\n"
                + "Selepas pembayaran, hantar resit ke:\n"
                + "  WhatsApp: " + supportWhatsApp + "\n"
                + "  Emel: " + supportEmail + "\n"
            );

            mailSender.send(message);
            log.info("Payment reminder sent for order {} to {}", order.getOrderNumber(), order.getCustomerEmail());
        } catch (Exception e) {
            log.error("Failed to send payment reminder for order {}: {}", order.getOrderNumber(), e.getMessage());
        }
    }
}
