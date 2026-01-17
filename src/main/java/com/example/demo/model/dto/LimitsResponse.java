package com.example.demo.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LimitsResponse {
    
    private List<LimitInfo> limits;
    private int totalPages;
    private long totalElements;
    private int currentPage;
    private int pageSize;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LimitInfo {
        private String apiKey;
        private Integer limitCount;
        private Integer windowSeconds;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}