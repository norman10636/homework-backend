package com.example.demo.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UsageResponse {
    
    private String apiKey;
    private Integer currentCount;
    private Integer limitCount;
    private Integer remaining;
    private Long windowTtl;
    private Integer windowSeconds;
}