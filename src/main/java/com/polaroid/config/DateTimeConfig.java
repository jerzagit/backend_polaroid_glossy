package com.polaroid.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.format.datetime.standard.DateTimeFormatterRegistrar;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.format.DateTimeFormatter;

@Configuration
public class DateTimeConfig implements WebMvcConfigurer {
    
    public static final String MALAYSIA_DATE_TIME_FORMAT = "dd/MM/yyyy hh:mm a";
    public static final String MALAYSIA_DATE_FORMAT = "dd/MM/yyyy";
    public static final DateTimeFormatter MALAYSIA_DATE_TIME_FORMATTER = 
            DateTimeFormatter.ofPattern(MALAYSIA_DATE_TIME_FORMAT);
    public static final DateTimeFormatter MALAYSIA_DATE_FORMATTER = 
            DateTimeFormatter.ofPattern(MALAYSIA_DATE_FORMAT);
    
    @Override
    public void addFormatters(FormatterRegistry registry) {
        DateTimeFormatterRegistrar registrar = new DateTimeFormatterRegistrar();
        registrar.setDateTimeFormatter(MALAYSIA_DATE_TIME_FORMATTER);
        registrar.setDateFormatter(MALAYSIA_DATE_FORMATTER);
        registrar.registerFormatters(registry);
    }
}
