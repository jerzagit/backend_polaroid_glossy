package com.polaroid.config;

import com.polaroid.model.PrintSize;
import com.polaroid.model.Review;
import com.polaroid.model.Testimonial;
import com.polaroid.repository.PrintSizeRepository;
import com.polaroid.repository.ReviewRepository;
import com.polaroid.repository.TestimonialRepository;
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
    private final TestimonialRepository testimonialRepository;
    private final ReviewRepository reviewRepository;

    @Bean
    CommandLineRunner seedPrintSizes() {
        return args -> {
            if (printSizeRepository.count() > 0) {
                return;
            }

            printSizeRepository.saveAll(List.of(
                    printSize("2R", "2R", "2R (2.5 x 3.5 inches)", "2.5", "3.5", "0.50", "Wallet size - Perfect for keepsakes", "MINI"),
                    printSize("3R", "3R", "3R (3.5 x 5 inches)", "3.5", "5", "0.75", "Standard photo size - Great for albums", "CLASSIC"),
                    printSize("4R", "4R", "4R (4 x 6 inches)", "4", "6", "1.00", "Most popular - Classic polaroid style", "BESTSELLER"),
                    printSize("A4", "A4", "A4 (8.3 x 11.7 inches)", "8.3", "11.7", "3.50", "Poster size - Perfect for displays", "PREMIUM")
            ));
        };
    }

    @Bean
    CommandLineRunner seedTestimonials() {
        return args -> {
            if (testimonialRepository.count() > 0) {
                return;
            }

            testimonialRepository.saveAll(List.of(
                    testimonial("Sarah Mitchell", "New York, USA", "Absolutely love my polaroid prints! The quality is amazing and they arrived so quickly. Perfect for my scrapbook!", "Sangat suka cetakan polaroid saya! Kualiti sangat mengagumkan dan tiba dengan cepat. Sempurna untuk buku skrap saya!", "4R Classic", "4R Klasik", "/images/customer-1.png", 5, 0),
                    testimonial("James & Emily", "London, UK", "We ordered prints for our anniversary and couldn't be happier. The custom text feature made them extra special!", "Kami memesan cetakan untuk ulang tahun kami dan sangat berpuas hati. Ciri teks khas menjadikannya lebih istimewa!", "Mixed Sizes", "Pelbagai Saiz", "/images/customer-2.png", 5, 1),
                    testimonial("Margaret & Tommy", "Sydney, Australia", "My grandson and I love looking through our polaroid memories together. Thank you for such beautiful quality!", "Cucu saya dan saya suka melihat kenangan polaroid bersama. Terima kasih atas kualiti yang sangat cantik!", "A4 Poster", "Poster A4", "/images/customer-3.png", 5, 2),
                    testimonial("Party Squad", "Toronto, Canada", "Ordered 50 prints for our friend's birthday party. Everyone loved taking home a memory! Great prices too.", "Memesan 50 cetakan untuk parti hari jadi kawan kami. Semua orang suka membawa pulang kenangan! Harga yang berpatutan juga.", "3R Standard", "3R Standard", "/images/customer-4.png", 5, 3)
            ));
        };
    }

    @Bean
    CommandLineRunner seedReviews() {
        return args -> {
            if (reviewRepository.count() > 0) {
                return;
            }

            reviewRepository.saveAll(List.of(
                    review(5, "Amazing quality!", "The print quality is incredible. Colors are vibrant and the paper feels premium. Will definitely order again!"),
                    review(5, "Perfect for gifts", "Ordered these as anniversary gifts. The customization options made them extra special. Fast delivery too!"),
                    review(5, "Beautiful memories", "My grandma cried when she saw the photos. The A4 poster size is stunning. Thank you for the quality!"),
                    review(4, "Great for parties", "Ordered 50 prints for my daughter's birthday. Everyone loved taking home instant photos. Quick turnaround!")
            ));
        };
    }

    private PrintSize printSize(String id, String name, String displayName, String width, String height, String price, String description, String tag) {
        return PrintSize.builder()
                .id(id)
                .name(name)
                .displayName(displayName)
                .width(new BigDecimal(width))
                .height(new BigDecimal(height))
                .price(new BigDecimal(price))
                .description(description)
                .isActive(true)
                .tag(tag)
                .build();
    }

    private Testimonial testimonial(String name, String location, String text, String textMy, String printType, String printTypeMy, String imageUrl, int rating, int sortOrder) {
        return Testimonial.builder()
                .name(name)
                .location(location)
                .text(text)
                .textMy(textMy)
                .printType(printType)
                .printTypeMy(printTypeMy)
                .imageUrl(imageUrl)
                .rating(rating)
                .sortOrder(sortOrder)
                .isActive(true)
                .build();
    }

    private Review review(int rating, String title, String comment) {
        return Review.builder()
                .rating(rating)
                .title(title)
                .comment(comment)
                .build();
    }
}
