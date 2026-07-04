package com.polaroid.service;

import com.polaroid.model.Order;
import com.polaroid.model.OrderItem;
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

    @Value("${app.frontend-url:https://polaroidglossy.my}")
    private String frontendUrl;

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

            message.setText(
                buildReceipt(order, "Order Confirmed / Pesanan Disahkan",
                    "Thank you for your order! Your order has been received and is being processed.",
                    "Your payment is pending. Please complete payment to avoid automatic cancellation.",
                    "Terima kasih atas pesanan anda! Pesanan anda telah diterima dan sedang diproses.",
                    "Pembayaran anda masih belum diterima. Sila selesaikan pembayaran untuk mengelakkan pembatalan automatik.")
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

            message.setText(
                buildReceipt(order, "Payment Received / Pembayaran Diterima",
                    "Your payment has been received! We are now processing your order.",
                    "You will receive a shipping notification once your order is on its way.",
                    "Pembayaran anda telah diterima! Kami sedang memproses pesanan anda.",
                    "Anda akan menerima notifikasi penghantaran selepas pesanan dihantar.")
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
                buildReminder(order, deadline)
            );

            mailSender.send(message);
            log.info("Payment reminder sent for order {} to {}", order.getOrderNumber(), order.getCustomerEmail());
        } catch (Exception e) {
            log.error("Failed to send payment reminder for order {}: {}", order.getOrderNumber(), e.getMessage());
        }
    }

    private String buildReceipt(Order order, String subject, String enIntro, String enFooter, String myIntro, String myFooter) {
        String orderUrl = frontendUrl + "?order=" + order.getOrderNumber();
        return separator() + subject + separator()
            + enLine(order, orderUrl, enIntro, enFooter)
            + separator()
            + myLine(order, orderUrl, myIntro, myFooter);
    }

    private String enLine(Order order, String orderUrl, String intro, String footer) {
        return "\nDear " + order.getCustomerName() + ",\n\n"
            + intro + "\n\n"
            + receiptBody(order, orderUrl) + "\n"
            + footer + "\n\n"
            + "Track your order: " + orderUrl + "\n\n"
            + "Thank you,\n"
            + "Polaroid Glossy MY\n";
    }

    private String myLine(Order order, String orderUrl, String intro, String footer) {
        return "\nYth. " + order.getCustomerName() + ",\n\n"
            + intro + "\n\n"
            + receiptBodyMy(order, orderUrl) + "\n"
            + footer + "\n\n"
            + "Semak pesanan anda: " + orderUrl + "\n\n"
            + "Terima kasih,\n"
            + "Polaroid Glossy MY\n";
    }

    private String receiptBody(Order order, String orderUrl) {
        StringBuilder sb = new StringBuilder();
        sb.append("================================\n");
        sb.append("         ORDER RECEIPT\n");
        sb.append("================================\n\n");
        sb.append("Order Number : ").append(order.getOrderNumber()).append("\n");
        sb.append("Order Date   : ").append(formatDateTime(order.getCreatedAt())).append("\n");
        sb.append("Status       : ").append(order.getStatus()).append("\n\n");
        sb.append("--- Items ---\n");
        for (OrderItem item : order.getItems()) {
            sb.append(String.format("  %-10s x %d  @ RM%.2f  = RM%.2f\n",
                item.getSizeName() != null ? item.getSizeId() : item.getSizeId(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getTotalPrice()));
        }
        sb.append("\n");
        sb.append(String.format("  Subtotal          : RM%.2f\n", order.getSubtotal()));
        sb.append(String.format("  Shipping          : RM%.2f\n", order.getShipping()));
        sb.append(String.format("  TOTAL             : RM%.2f\n", order.getTotal()));
        sb.append("\n--- Shipping To ---\n");
        sb.append("  ").append(order.getCustomerName()).append("\n");
        sb.append("  ").append(order.getCustomerHouseUnitNo() != null ? order.getCustomerHouseUnitNo() + ", " : "");
        sb.append(order.getCustomerAddressLine1()).append("\n");
        if (order.getCustomerAddressLine2() != null && !order.getCustomerAddressLine2().isBlank()) {
            sb.append("  ").append(order.getCustomerAddressLine2()).append("\n");
        }
        sb.append("  ").append(order.getCustomerPostcode()).append(" ").append(order.getCustomerCity()).append("\n");
        sb.append("  ").append(order.getCustomerState()).append(", ").append(order.getCustomerCountry()).append("\n");
        sb.append("  Phone: ").append(order.getCustomerPhone() != null ? order.getCustomerPhone() : "-").append("\n\n");
        return sb.toString();
    }

    private String receiptBodyMy(Order order, String orderUrl) {
        StringBuilder sb = new StringBuilder();
        sb.append("================================\n");
        sb.append("         RESIT PESANAN\n");
        sb.append("================================\n\n");
        sb.append("Nombor Pesanan : ").append(order.getOrderNumber()).append("\n");
        sb.append("Tarikh Pesanan : ").append(formatDateTime(order.getCreatedAt())).append("\n");
        sb.append("Status         : ").append(order.getStatus()).append("\n\n");
        sb.append("--- Item ---\n");
        for (OrderItem item : order.getItems()) {
            sb.append(String.format("  %-10s x %d  @ RM%.2f  = RM%.2f\n",
                item.getSizeName() != null ? item.getSizeId() : item.getSizeId(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getTotalPrice()));
        }
        sb.append("\n");
        sb.append(String.format("  Subjumlah         : RM%.2f\n", order.getSubtotal()));
        sb.append(String.format("  Penghantaran      : RM%.2f\n", order.getShipping()));
        sb.append(String.format("  JUMLAH            : RM%.2f\n", order.getTotal()));
        sb.append("\n--- Dihantar Ke ---\n");
        sb.append("  ").append(order.getCustomerName()).append("\n");
        sb.append("  ").append(order.getCustomerHouseUnitNo() != null ? order.getCustomerHouseUnitNo() + ", " : "");
        sb.append(order.getCustomerAddressLine1()).append("\n");
        if (order.getCustomerAddressLine2() != null && !order.getCustomerAddressLine2().isBlank()) {
            sb.append("  ").append(order.getCustomerAddressLine2()).append("\n");
        }
        sb.append("  ").append(order.getCustomerPostcode()).append(" ").append(order.getCustomerCity()).append("\n");
        sb.append("  ").append(order.getCustomerState()).append(", ").append(order.getCustomerCountry()).append("\n");
        sb.append("  Telefon: ").append(order.getCustomerPhone() != null ? order.getCustomerPhone() : "-").append("\n\n");
        return sb.toString();
    }

    private String buildReminder(Order order, String deadline) {
        String orderUrl = frontendUrl + "?order=" + order.getOrderNumber();
        return separator() + "Payment Reminder / Peringatan Pembayaran" + separator()
            + "\nDear " + order.getCustomerName() + ",\n\n"
            + "This is a reminder that your payment for order " + order.getOrderNumber() + " is still pending.\n"
            + "Please make your payment before " + deadline + " to avoid automatic cancellation.\n\n"
            + receiptBody(order, orderUrl)
            + "--- Bank Transfer Details ---\n"
            + "  Bank          : Maybank\n"
            + "  Account Name  : Acachiaa Empire\n"
            + "  Account No.   : 123456789012\n"
            + "  Amount        : RM" + String.format("%.2f", order.getTotal()) + "\n"
            + "  Reference     : " + order.getOrderNumber() + "\n\n"
            + "After payment, send your receipt to:\n"
            + "  WhatsApp: " + supportWhatsApp + "\n"
            + "  Email: " + supportEmail + "\n\n"
            + "Track your order: " + orderUrl + "\n\n"
            + "Thank you,\n"
            + "Polaroid Glossy MY\n"
            + separator()
            + "\nYth. " + order.getCustomerName() + ",\n\n"
            + "Ini adalah peringatan bahawa pembayaran untuk pesanan " + order.getOrderNumber() + " masih belum diterima.\n"
            + "Sila buat pembayaran sebelum " + deadline + " untuk mengelakkan pembatalan automatik.\n\n"
            + receiptBodyMy(order, orderUrl)
            + "--- Butiran Pemindahan Bank ---\n"
            + "  Bank          : Maybank\n"
            + "  Nama Akaun    : Acachiaa Empire\n"
            + "  No. Akaun     : 123456789012\n"
            + "  Jumlah        : RM" + String.format("%.2f", order.getTotal()) + "\n"
            + "  Rujukan       : " + order.getOrderNumber() + "\n\n"
            + "Selepas pembayaran, hantar resit ke:\n"
            + "  WhatsApp: " + supportWhatsApp + "\n"
            + "  Emel: " + supportEmail + "\n\n"
            + "Semak pesanan anda: " + orderUrl + "\n\n"
            + "Terima kasih,\n"
            + "Polaroid Glossy MY\n";
    }

    private String separator() {
        return "\n========================================\n";
    }

    private String formatDateTime(LocalDateTime dt) {
        if (dt == null) return "-";
        return dt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a"));
    }
}
