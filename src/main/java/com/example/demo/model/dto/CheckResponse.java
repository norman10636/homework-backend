package com.example.demo.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CheckResponse {
    
    private boolean allowed;
    private String message;
    private Integer currentCount;
    private Integer limitCount;
    private Long remainingTtl;
    
    public CheckResponse(boolean allowed, String message) {
        this.allowed = allowed;
        this.message = message;
    }
}