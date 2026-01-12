package com.example.demo.controller;

import com.example.demo.model.dto.CreateLimitRequest;
import com.example.demo.service.RateLimitService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RateLimitController.class)
public class RateLimitControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private RateLimitService rateLimitService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Test
    public void testHealthEndpoint() throws Exception {
        mockMvc.perform(get("/health"))
            .andExpect(status().isOk())
            .andExpect(content().string("Rate Limiter Service is running"));
    }
    
    @Test
    public void testCreateLimitValidation() throws Exception {
        CreateLimitRequest invalidRequest = new CreateLimitRequest("", -1, 0);
        
        mockMvc.perform(post("/limits")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
            .andExpect(status().isBadRequest());
    }
}