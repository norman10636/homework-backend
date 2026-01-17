package com.example.demo.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateLimitRequest {
    
    @NotBlank(message = "API key cannot be blank")
    private String apiKey;
    
    @Positive(message = "Limit must be positive")
    private Integer limit;
    
    @Positive(message = "Window seconds must be positive")
    private Integer windowSeconds;
}