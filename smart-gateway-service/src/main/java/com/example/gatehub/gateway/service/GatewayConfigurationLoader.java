package com.example.gatehub.gateway.service;

import com.example.gatehub.gateway.client.ManagementApiClient;
import com.example.gatehub.gateway.dto.GatewayRoute;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GatewayConfigurationLoader implements ApplicationRunner {
    private final ManagementApiClient managementApiClient;
    private final RouteRegistry routeRegistry;

    public GatewayConfigurationLoader(ManagementApiClient managementApiClient,
                                      RouteRegistry routeRegistry) {
        this.managementApiClient = managementApiClient;
        this.routeRegistry = routeRegistry;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        refresh();
    }

    public void refresh() {
        List<GatewayRoute> routes = managementApiClient.fetchActiveRoutes();

        routeRegistry.replaceALl(routes);
        //demo
//        for(GatewayRoute route : routes) {
//            System.out.println("Route : " + route.getTargetPathPrefix());
//        }
    }
}
