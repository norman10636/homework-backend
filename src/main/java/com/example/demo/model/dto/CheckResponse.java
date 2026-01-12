package com.example.demo.model.dto;

public class CheckResponse {
    
    private boolean allowed;
    private String message;
    private Integer currentCount;
    private Integer limitCount;
    private Long remainingTtl;
    
    public CheckResponse() {}
    
    public CheckResponse(boolean allowed, String message) {
        this.allowed = allowed;
        this.message = message;
    }
    
    public CheckResponse(boolean allowed, String message, Integer currentCount, Integer limitCount, Long remainingTtl) {
        this.allowed = allowed;
        this.message = message;
        this.currentCount = currentCount;
        this.limitCount = limitCount;
        this.remainingTtl = remainingTtl;
    }
    
    public boolean isAllowed() {
        return allowed;
    }
    
    public void setAllowed(boolean allowed) {
        this.allowed = allowed;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public Integer getCurrentCount() {
        return currentCount;
    }
    
    public void setCurrentCount(Integer currentCount) {
        this.currentCount = currentCount;
    }
    
    public Integer getLimitCount() {
        return limitCount;
    }
    
    public void setLimitCount(Integer limitCount) {
        this.limitCount = limitCount;
    }
    
    public Long getRemainingTtl() {
        return remainingTtl;
    }
    
    public void setRemainingTtl(Long remainingTtl) {
        this.remainingTtl = remainingTtl;
    }
}