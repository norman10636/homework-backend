package com.example.demo.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "api_limits")
public class ApiLimit {
    
    @Id
    @Column(name = "api_key", nullable = false)
    @NotBlank(message = "API key cannot be blank")
    private String apiKey;
    
    @Column(name = "limit_count", nullable = false)
    @Positive(message = "Limit count must be positive")
    private Integer limitCount;
    
    @Column(name = "window_seconds", nullable = false)
    @Positive(message = "Window seconds must be positive")
    private Integer windowSeconds;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    public ApiLimit(String apiKey, Integer limitCount, Integer windowSeconds) {
        this.apiKey = apiKey;
        this.limitCount = limitCount;
        this.windowSeconds = windowSeconds;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}