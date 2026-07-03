package com.example.poc.mockservice;

import com.example.poc.mockservice.config.CryptoProfilesProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(CryptoProfilesProperties.class)
public class MockSecureServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(MockSecureServiceApplication.class, args);
    }
}
