package com.example.gatehub.management.dto.responseDto;

import com.example.gatehub.management.entity.RouteHeaderRule;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class HeaderRuleResponse {

    private Long id;
    private String direction;
    private String action;
    private String headerName;
    private String headerValue;

    public static HeaderRuleResponse from(RouteHeaderRule rule) {
        return new HeaderRuleResponse(rule.getId(), rule.getDirection(),
                rule.getAction(), rule.getHeaderName(), rule.getHeaderValue());
    }
}
