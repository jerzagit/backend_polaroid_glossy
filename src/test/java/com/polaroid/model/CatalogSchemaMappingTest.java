package com.polaroid.model;

import com.polaroid.model.enums.ProductStyle;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Validates the V26 / V27 / V29 catalog migrations against the JPA entity mappings. */
class CatalogSchemaMappingTest {

    @Test
    void validatesProductsCatalogMigrationsAndReadsDecimalPrices() throws Exception {
        String url = "jdbc:h2:mem:catalog_mapping;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            try (var statement = connection.createStatement()) {
                // Minimal legacy schema that V26 / V27 build on.
                statement.execute("CREATE TABLE print_sizes ("
                        + "id VARCHAR(255) PRIMARY KEY, name VARCHAR(255), display_name VARCHAR(255),"
                        + "width DECIMAL(5,2), height DECIMAL(5,2), price DECIMAL(10,2),"
                        + "description TEXT, is_active BOOLEAN, tag VARCHAR(50))");
                statement.execute("CREATE TABLE print_size_pricing_tiers ("
                        + "id VARCHAR(50) PRIMARY KEY, size_id VARCHAR(50) NOT NULL,"
                        + "quantity INT NOT NULL, regular_price DECIMAL(10,2) NOT NULL,"
                        + "discounted_price DECIMAL(10,2) NOT NULL, sort_order INT DEFAULT 0)");
                statement.execute("CREATE TABLE product_meta ("
                        + "id VARCHAR(255) PRIMARY KEY, tag VARCHAR(50), accent_color VARCHAR(32),"
                        + "images TEXT, features TEXT, tiktok_videos TEXT, short_description TEXT,"
                        + "full_description TEXT, rating DECIMAL(3,2), review_count INT, popular BOOLEAN)");

                statement.execute("INSERT INTO print_sizes (id, name, display_name, price, is_active) VALUES"
                        + " ('ic', 'IC', 'IC', 2.00, TRUE),"
                        + " ('ic-border', 'IC Border', 'IC Border', 2.50, TRUE),"
                        + " ('2r-no-border', '2R', '2R', 2.20, TRUE),"
                        + " ('2r-border', '2R Border', '2R Border', 2.00, TRUE),"
                        + " ('3r-no-border', '3R', '3R', 2.50, TRUE)");
                statement.execute("INSERT INTO product_meta (id, tag) VALUES"
                        + " ('2r-no-border', '2R'), ('3r-no-border', '3R')");
                statement.execute("INSERT INTO print_size_pricing_tiers VALUES"
                        + " ('tier-2r-10', '2r-no-border', 10, 7.00, 7.00, 1)");
            }

            runScript(connection, "V26__Create_products_catalog.sql");
            runScript(connection, "V27__Seed_products_and_retire_duplicate_sizes.sql");
            runScript(connection, "V29__Add_variant_cover_image.sql");

            try (var statement = connection.createStatement()) {
                var rs = statement.executeQuery(
                        "SELECT COUNT(*) FROM products WHERE is_active = TRUE");
                rs.next();
                assertEquals(10, rs.getInt(1));

                rs = statement.executeQuery(
                        "SELECT p.id, v.style, pkg.price FROM product_packages pkg"
                        + " JOIN product_variants v ON v.id = pkg.variant_id"
                        + " JOIN products p ON p.id = v.product_id"
                        + " WHERE p.code = 'ic' AND v.style = 'FULL' AND pkg.quantity = 10");
                rs.next();
                long icId = rs.getLong(1);
                assertEquals(new BigDecimal("6.00"), rs.getBigDecimal(3));

                rs = statement.executeQuery(
                        "SELECT pkg.price FROM product_packages pkg"
                        + " JOIN product_variants v ON v.id = pkg.variant_id"
                        + " JOIN products p ON p.id = v.product_id"
                        + " WHERE p.code = 'ic' AND v.style = 'BORDER' AND pkg.quantity = 10");
                rs.next();
                assertEquals(new BigDecimal("5.00"), rs.getBigDecimal(1));

                // IC has no 25-print package (the retired 25 tier is gone).
                rs = statement.executeQuery(
                        "SELECT COUNT(*) FROM product_packages pkg"
                        + " JOIN product_variants v ON v.id = pkg.variant_id"
                        + " JOIN products p ON p.id = v.product_id"
                        + " WHERE p.code = 'ic' AND pkg.quantity = 25");
                rs.next();
                assertEquals(0, rs.getInt(1));

                // 4R offers Full only.
                rs = statement.executeQuery(
                        "SELECT COUNT(*) FROM product_variants v JOIN products p ON p.id = v.product_id"
                        + " WHERE p.code = '4r' AND v.style = 'BORDER'");
                rs.next();
                assertEquals(0, rs.getInt(1));

                rs = statement.executeQuery(
                        "SELECT product_id, style FROM product_aliases WHERE legacy_code = 'ic-border'");
                rs.next();
                assertEquals(icId, rs.getLong(1));
                assertEquals("BORDER", rs.getString(2));

                rs = statement.executeQuery(
                        "SELECT is_active FROM print_sizes WHERE id = '2r-no-border'");
                rs.next();
                assertFalse(rs.getBoolean(1));

                rs = statement.executeQuery(
                        "SELECT COUNT(*) FROM print_size_pricing_tiers WHERE size_id = '2r-no-border'");
                rs.next();
                assertEquals(0, rs.getInt(1));
            }
        }

        try (var factory = new Configuration()
                .addAnnotatedClass(Product.class)
                .addAnnotatedClass(ProductAlias.class)
                .addAnnotatedClass(ProductVariant.class)
                .addAnnotatedClass(ProductPackage.class)
                .setProperty("hibernate.connection.url", url)
                .setProperty("hibernate.connection.username", "sa")
                .setProperty("hibernate.connection.password", "")
                .setProperty("hibernate.hbm2ddl.auto", "validate")
                .buildSessionFactory();
             var session = factory.openSession()) {
            Product ic = session.createQuery("from Product where code = 'ic'", Product.class).getSingleResult();
            assertEquals("IC", ic.getName());
            assertEquals(new BigDecimal("2.00"), ic.getBasePrice());

            ProductVariant borderVariant = session.createQuery(
                            "from ProductVariant where productId = :pid and style = :style", ProductVariant.class)
                    .setParameter("pid", ic.getId())
                    .setParameter("style", ProductStyle.BORDER)
                    .getSingleResult();
            assertEquals("Border (Polaroid)", borderVariant.getLabel());

            ProductPackage pkg = session.createQuery(
                            "from ProductPackage where variantId = :vid and quantity = 10", ProductPackage.class)
                    .setParameter("vid", borderVariant.getId())
                    .getSingleResult();
            assertEquals(new BigDecimal("5.00"), pkg.getPrice());
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
