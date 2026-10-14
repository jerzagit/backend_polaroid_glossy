package com.polaroid.model;

import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProductMetaMappingTest {
    @Test
    void validatesMigrationSchemaAndReadsDecimalRating() throws Exception {
        String url = "jdbc:h2:mem:product_meta_mapping;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V18__Create_product_meta_and_pricing_tiers.sql"));
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            statement.execute(migration.substring(0, migration.indexOf(';') + 1));
            statement.execute("INSERT INTO product_meta (id, rating) VALUES ('ic-border', 4.90)");
        }
        try (var factory = new Configuration()
                .addAnnotatedClass(ProductMeta.class)
                .setProperty("hibernate.connection.url", url)
                .setProperty("hibernate.connection.username", "sa")
                .setProperty("hibernate.connection.password", "")
                .setProperty("hibernate.hbm2ddl.auto", "validate")
                .buildSessionFactory();
             var session = factory.openSession()) {
            assertEquals(new BigDecimal("4.90"), session.get(ProductMeta.class, "ic-border").getRating());
        }
    }
}
