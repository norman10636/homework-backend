package com.example.demo.model.dto;

import java.time.LocalDateTime;
import java.util.List;

public class LimitsResponse {
    
    private List<LimitInfo> limits;
    private int totalPages;
    private long totalElements;
    private int currentPage;
    private int pageSize;
    
    public LimitsResponse() {}
    
    public LimitsResponse(List<LimitInfo> limits, int totalPages, long totalElements, int currentPage, int pageSize) {
        this.limits = limits;
        this.totalPages = totalPages;
        this.totalElements = totalElements;
        this.currentPage = currentPage;
        this.pageSize = pageSize;
    }
    
    public List<LimitInfo> getLimits() {
        return limits;
    }
    
    public void setLimits(List<LimitInfo> limits) {
        this.limits = limits;
    }
    
    public int getTotalPages() {
        return totalPages;
    }
    
    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }
    
    public long getTotalElements() {
        return totalElements;
    }
    
    public void setTotalElements(long totalElements) {
        this.totalElements = totalElements;
    }
    
    public int getCurrentPage() {
        return currentPage;
    }
    
    public void setCurrentPage(int currentPage) {
        this.currentPage = currentPage;
    }
    
    public int getPageSize() {
        return pageSize;
    }
    
    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }
    
    public static class LimitInfo {
        private String apiKey;
        private Integer limitCount;
        private Integer windowSeconds;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        
        public LimitInfo() {}
        
        public LimitInfo(String apiKey, Integer limitCount, Integer windowSeconds, LocalDateTime createdAt, LocalDateTime updatedAt) {
            this.apiKey = apiKey;
            this.limitCount = limitCount;
            this.windowSeconds = windowSeconds;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }
        
        public String getApiKey() {
            return apiKey;
        }
        
        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }
        
        public Integer getLimitCount() {
            return limitCount;
        }
        
        public void setLimitCount(Integer limitCount) {
            this.limitCount = limitCount;
        }
        
        public Integer getWindowSeconds() {
            return windowSeconds;
        }
        
        public void setWindowSeconds(Integer windowSeconds) {
            this.windowSeconds = windowSeconds;
        }
        
        public LocalDateTime getCreatedAt() {
            return createdAt;
        }
        
        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }
        
        public LocalDateTime getUpdatedAt() {
            return updatedAt;
        }
        
        public void setUpdatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
        }
    }
}