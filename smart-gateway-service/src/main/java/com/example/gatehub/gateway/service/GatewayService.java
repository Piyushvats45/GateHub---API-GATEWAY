package com.example.gatehub.gateway.service;

import com.example.gatehub.gateway.modal.CompiledRoute;
import com.example.gatehub.gateway.dto.GatewayErrorResponse;
import com.example.gatehub.gateway.dto.GatewayRoute;
import com.example.gatehub.gateway.modal.RateLimitResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class GatewayService {

    private final RouteRegistry routeRegistry;
    private ObjectMapper objectMapper;
    private final RateLimitingService rateLimitingService;
    private final IdempotencyService idempotencyService;

    public GatewayService(RouteRegistry routeRegistry,
                          ObjectMapper objectMapper,
                          RateLimitingService rateLimitingService,
                          IdempotencyService idempotencyService){
        this.routeRegistry = routeRegistry;
        this.objectMapper = objectMapper;
        this.rateLimitingService = rateLimitingService;
        this.idempotencyService = idempotencyService;
    }

    public ResponseEntity<byte[]> handle(HttpServletRequest request){
        long startedAt = System.nanoTime();

        String correlationId = correlationId(request);

        String outcome = "TARGET_ERROR";
        int statusCode = HttpStatus.INTERNAL_SERVER_ERROR.value();
        boolean rateLimited = false;
        HttpHeaders policyHeaders = new HttpHeaders();

        //1. compiled route for incoming HTTP request
        Optional<CompiledRoute> matchedRoute = routeRegistry.find(request.getRequestURI());

        // some validations
        if(!matchedRoute.isPresent()){
            outcome = "ROUTE_NOT_FOUND";
            statusCode = HttpStatus.NOT_FOUND.value();

            return error(HttpStatus.NOT_FOUND, "No active route matches", correlationId, null);
        }

        CompiledRoute compiledRoute = matchedRoute.get();
        GatewayRoute route = compiledRoute.getGatewayRoute();

        // validaton for allowed methods
        if(!route.getAllowedMethods().contains(request.getMethod())){
            outcome = "METHOD_NOT_ALLOWED";
            statusCode = HttpStatus.METHOD_NOT_ALLOWED.value();

            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.ALLOW, String.join(", ", route.getAllowedMethods()));

            return error(HttpStatus.METHOD_NOT_ALLOWED, "HTTP Method is not allowed for this route", correlationId, headers);
        }
        //2. To find rate limiting rules and apply it
        RateLimitResult rateLimit = rateLimitingService.check(route, request.getRemoteAddr());
        policyHeaders = rateLimitHeaders(rateLimit);

        if(!rateLimit.isAllowed()){
            rateLimited = true;
            outcome = "RATE_LIMITED";
            statusCode = HttpStatus.TOO_MANY_REQUESTS.value();

            policyHeaders.set(HttpHeaders.RETRY_AFTER, String.valueOf(rateLimit.getRetryAfterSeconds()));

            return error(HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded", correlationId, policyHeaders);
        }

        //3. idempotency check
        if(route.isIdempotencyEnabled() && "POST".equals(request.getMethod())){
//            idempotencyService.
        }

        //4. header rules

        //5. redirect our end point to target

        //6. header rules on response

        //7. store response if idempotency is enabled

        //8. return response to client
    }

    private String correlationId(HttpServletRequest request){
        String correlationId = request.getHeader("X-Correlation-ID");
        if(correlationId == null || correlationId.trim().isEmpty()){
            return UUID.randomUUID().toString();
        }
        return correlationId;
    }

    private HttpHeaders rateLimitHeaders(RateLimitResult result){
        HttpHeaders headers = new HttpHeaders();
        if(result.getLimit() > 0){
            headers.set("X-RateLimit-Limit", String.valueOf(result.getLimit()));
            headers.set("X-RateLimit-Remaining", String.valueOf(result.getRemaining()));
        }
        return headers;
    }

    private ResponseEntity<byte[]> error(HttpStatus status, String message,
                                         String correlationId,
                                         HttpHeaders additionalHeadrers){
        GatewayErrorResponse response = new GatewayErrorResponse(status.value(),
                message, correlationId, LocalDateTime.now());

        HttpHeaders headers = additionalHeadrers == null ? new HttpHeaders() : additionalHeadrers;

        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Correlation-ID", correlationId);

        return ResponseEntity.status(status).headers(headers)
                .body(objectMapper.writeValueAsString(response)
                        .getBytes(StandardCharsets.UTF_8));
    }
}
