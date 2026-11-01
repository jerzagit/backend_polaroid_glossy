package com.polaroid.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.polaroid.dto.mapper.OrderMapper;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class OrderProductPersistenceTest {
    @Test
    void productSelectionSurvivesDatabaseReloadAndAppearsInResponse() throws Exception {
        try (var factory = new Configuration()
                .addAnnotatedClass(Order.class)
                .addAnnotatedClass(OrderItem.class)
                .addAnnotatedClass(OrderStatusHistory.class)
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:order_product;MODE=PostgreSQL")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop")
                .buildSessionFactory();
             var session = factory.openSession()) {
            // Exercise the migration on a schema without the new column.
            session.doWork(connection -> {
                try (var statement = connection.createStatement()) {
                    statement.execute("ALTER TABLE order_items DROP COLUMN product_id");
                    statement.execute(Files.readString(Path.of(
                            "src/main/resources/db/migration/V31__Add_order_item_product_id.sql")));
                } catch (java.io.IOException e) {
                    throw new IllegalStateException(e);
                }
            });
            var order = Order.builder().orderNumber("PRODUCT-TEST")
                    .customerName("Test Customer").customerEmail("test@example.com")
                    .total(new BigDecimal("12.00")).build();
            var item = OrderItem.builder().order(order).productId(1L)
                    .sizeId("ic").sizeName("IC (5.5 x 8.9 cm)")
                    .variantId("10").variantLabel("Full").style("FULL")
                    .packageId("100").packageQuantity(10).packageCount(2)
                    .packagePrice(new BigDecimal("6.00")).quantity(20)
                    .unitPrice(new BigDecimal("6.00")).totalPrice(new BigDecimal("12.00")).build();
            order.getItems().add(item);
            var legacy = OrderItem.builder().order(order).sizeId("ic-border").sizeName("IC Border")
                    .quantity(1).unitPrice(BigDecimal.ONE).totalPrice(BigDecimal.ONE).build();
            order.getItems().add(legacy);
            var transaction = session.beginTransaction();
            session.persist(order);
            transaction.commit();
            session.clear();

            var saved = session.get(Order.class, order.getId());
            var response = new OrderMapper(new ObjectMapper(), null).toDto(saved);
            var selected = response.getItems().stream().filter(i -> i.getPackageId() != null).findFirst().orElseThrow();
            assertEquals(1L, selected.getProductId());
            assertEquals("IC (5.5 x 8.9 cm)", selected.getSizeName());
            assertEquals("Full", selected.getVariantLabel());
            assertEquals("100", selected.getPackageId());
            assertEquals(2, selected.getPackageCount());
            assertEquals(20, selected.getQuantity());
            assertEquals(new BigDecimal("12.00"), selected.getTotalPrice());
            assertNull(response.getItems().stream().filter(i -> i.getPackageId() == null).findFirst().orElseThrow().getProductId());
        }
    }
}
