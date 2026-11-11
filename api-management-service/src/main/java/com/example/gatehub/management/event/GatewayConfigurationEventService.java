package com.example.gatehub.management.event;

import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class GatewayConfigurationEventService {

    private final ApplicationEventPublisher eventPublisher;

    public GatewayConfigurationEventService(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void publish(String changeType, Long organizationId, String routeId,
                        Long configurationVersion) {


        eventPublisher.publishEvent(new GatewayConfigurationChangeEvent(
                UUID.randomUUID().toString(), "GATEWAY_CONFIGURATION_CHANGED", changeType,
                organizationId, routeId, configurationVersion, LocalDateTime.now()));
    }
}
