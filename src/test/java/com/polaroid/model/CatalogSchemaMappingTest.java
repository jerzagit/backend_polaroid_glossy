package com.polaroid.model;

import com.polaroid.model.enums.ProductStyle;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Validates the V23 catalog migrations against the JPA entity mappings. */
class CatalogSchemaMappingTest {

    @Test
    void validatesCatalogMigrationsAndReadsDecimalPrices() throws Exception {
        String url = "jdbc:h2:mem:catalog_mapping;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE print_sizes (id VARCHAR(50) PRIMARY KEY)");
                statement.execute("CREATE TABLE print_size_pricing_tiers ("
                        + "id VARCHAR(50) PRIMARY KEY, size_id VARCHAR(50) NOT NULL,"
                        + "quantity INT NOT NULL, regular_price DECIMAL(10,2) NOT NULL,"
                        + "discounted_price DECIMAL(10,2) NOT NULL, sort_order INT DEFAULT 0)");
                statement.execute("INSERT INTO print_sizes (id) VALUES ('ic')");
                statement.execute("INSERT INTO print_size_pricing_tiers VALUES"
                        + " ('tier-ic-25', 'ic', 25, 5.00, 4.50, 2)");
            }
            runScript(connection, "V23__Create_product_variants_and_packages.sql");
            runScript(connection, "V24__Seed_ic_variants_and_packages.sql");
            try (var statement = connection.createStatement()) {
                var rs = statement.executeQuery(
                        "SELECT price FROM product_packages WHERE id = 'ic-full-10'");
                rs.next();
                assertEquals(new BigDecimal("6.00"), rs.getBigDecimal(1));
                rs = statement.executeQuery(
                        "SELECT price FROM product_packages WHERE id = 'ic-border-10'");
                rs.next();
                assertEquals(new BigDecimal("5.00"), rs.getBigDecimal(1));
                rs = statement.executeQuery(
                        "SELECT COUNT(*) FROM product_packages WHERE quantity = 25");
                rs.next();
                assertEquals(0, rs.getInt(1));
                rs = statement.executeQuery(
                        "SELECT COUNT(*) FROM print_size_pricing_tiers WHERE size_id IN ('ic', 'ic-border')");
                rs.next();
                assertEquals(0, rs.getInt(1));
            }
        }
        try (var factory = new Configuration()
                .addAnnotatedClass(ProductVariant.class)
                .addAnnotatedClass(ProductPackage.class)
                .setProperty("hibernate.connection.url", url)
                .setProperty("hibernate.connection.username", "sa")
                .setProperty("hibernate.connection.password", "")
                .setProperty("hibernate.hbm2ddl.auto", "validate")
                .buildSessionFactory();
             var session = factory.openSession()) {
            ProductPackage pkg = session.get(ProductPackage.class, "ic-border-10");
            assertEquals(new BigDecimal("5.00"), pkg.getPrice());
            assertEquals(10, pkg.getQuantity());

            ProductVariant variant = session.get(ProductVariant.class, "ic-border");
            assertEquals(ProductStyle.BORDER, variant.getStyle());
            assertEquals("Border (Polaroid)", variant.getLabel());
            assertEquals("ic", variant.getProductId());
        }
    }

    private void runScript(java.sql.Connection connection, String fileName) throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration", fileName));
        String executable = stripComments(sql);
        try (var statement = connection.createStatement()) {
            for (String chunk : executable.split(";")) {
                if (!chunk.trim().isEmpty()) {
                    statement.execute(chunk.trim());
                }
            }
        }
    }

    private String stripComments(String sql) {
        StringBuilder out = new StringBuilder();
        for (String line : sql.split("\n")) {
            if (!line.trim().startsWith("--")) {
                out.append(line).append('\n');
            }
        }
        return out.toString();
    }
}
