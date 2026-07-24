package com.example.poc.mockservice.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class DemoDataInitializer implements ApplicationRunner {
    private final FulfillmentService fulfillmentService;
    private final PartnerKeyService partnerKeyService;

    public DemoDataInitializer(
            FulfillmentService fulfillmentService,
            PartnerKeyService partnerKeyService) {
        this.fulfillmentService = fulfillmentService;
        this.partnerKeyService = partnerKeyService;
    }

    @Override
    public void run(ApplicationArguments args) {
        fulfillmentService.resetDemoData();
        partnerKeyService.ensureSeededPartner();
    }
}
