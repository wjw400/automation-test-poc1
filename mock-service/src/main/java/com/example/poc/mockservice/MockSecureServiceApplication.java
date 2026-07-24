package com.example.poc.mockservice;

import com.example.poc.mockservice.config.CryptoConfigurationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(CryptoConfigurationProperties.class)
public class MockSecureServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(MockSecureServiceApplication.class, args);
    }
}
