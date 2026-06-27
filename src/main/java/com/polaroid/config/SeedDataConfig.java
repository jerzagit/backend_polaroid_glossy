package com.polaroid.config;

import com.polaroid.model.PrintSize;
import com.polaroid.repository.PrintSizeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.List;

@Configuration
@RequiredArgsConstructor
public class SeedDataConfig {

    private final PrintSizeRepository printSizeRepository;

    @Bean
    CommandLineRunner seedPrintSizes() {
        return args -> {
            if (printSizeRepository.count() > 0) {
                return;
            }

            printSizeRepository.saveAll(List.of(
                    printSize("2R", "2R", "2R (2.5 x 3.5 inches)", "2.5", "3.5", "0.50", "Wallet size - Perfect for keepsakes"),
                    printSize("3R", "3R", "3R (3.5 x 5 inches)", "3.5", "5", "0.75", "Standard photo size - Great for albums"),
                    printSize("4R", "4R", "4R (4 x 6 inches)", "4", "6", "1.00", "Most popular - Classic polaroid style"),
                    printSize("A4", "A4", "A4 (8.3 x 11.7 inches)", "8.3", "11.7", "3.50", "Poster size - Perfect for displays")
            ));
        };
    }

    private PrintSize printSize(String id, String name, String displayName, String width, String height, String price, String description) {
        return PrintSize.builder()
                .id(id)
                .name(name)
                .displayName(displayName)
                .width(new BigDecimal(width))
                .height(new BigDecimal(height))
                .price(new BigDecimal(price))
                .description(description)
                .isActive(true)
                .build();
    }
}
