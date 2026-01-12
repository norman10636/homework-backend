package com.example.demo.repository;

import com.example.demo.model.ApiLimit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ApiLimitRepository extends JpaRepository<ApiLimit, String> {
    
    Optional<ApiLimit> findByApiKey(String apiKey);
    
    Page<ApiLimit> findAllByOrderByCreatedAtDesc(Pageable pageable);
    
    void deleteByApiKey(String apiKey);
    
    boolean existsByApiKey(String apiKey);
}