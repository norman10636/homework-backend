package com.example.demo.service;

import com.example.demo.config.MySQLTestContainerConfig;
import com.example.demo.model.ApiLimit;
import com.example.demo.model.dto.CheckResponse;
import com.example.demo.model.dto.CreateLimitRequest;
import com.example.demo.model.dto.LimitsResponse;
import com.example.demo.model.dto.UsageResponse;
import com.example.demo.mq.MessageProducer;
import com.example.demo.repository.ApiLimitRepository;
import org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlGroup;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = MySQLTestContainerConfig.Initializer.class)
@EnableAutoConfiguration(exclude = RocketMQAutoConfiguration.class)
@DisplayName("RateLimitService Integration Tests with Testcontainers")
@Sql(scripts = "/sql/init-schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class RateLimitServiceIntegrationTest {

    @Autowired
    private RateLimitService rateLimitService;

    @Autowired
    private ApiLimitRepository apiLimitRepository;

    @MockBean
    private RedisService redisService;

    @MockBean
    private MessageProducer messageProducer;

    // ========== 建立限流配置測試 ==========

    @Nested
    @DisplayName("Create Limit Tests")
    @SqlGroup({
            @Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD),
            @Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    })
    class CreateLimitTests {

        @Test
        @DisplayName("Should create and persist limit to database")
        void shouldCreateAndPersistLimitToDatabase() {
            // Given
            given(redisService.getCachedApiLimitConfig(anyString())).willReturn(null);
            CreateLimitRequest request = new CreateLimitRequest("new-api-key", 100, 60);

            // When
            ApiLimit result = rateLimitService.createLimit(request);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getApiKey()).isEqualTo("new-api-key");
            assertThat(result.getLimitCount()).isEqualTo(100);
            assertThat(result.getWindowSeconds()).isEqualTo(60);

            // 驗證資料庫確實有寫入
            Optional<ApiLimit> saved = apiLimitRepository.findByApiKey("new-api-key");
            assertThat(saved).isPresent();
            assertThat(saved.get().getLimitCount()).isEqualTo(100);
        }
    }

    // ========== 讀取測試（使用預先插入的資料） ==========

    @Nested
    @DisplayName("Read Limit Tests - With Pre-inserted Data")
    @SqlGroup({
            @Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD),
            @Sql(scripts = "/sql/insert-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD),
            @Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    })
    class ReadLimitTests {

        @Test
        @DisplayName("Should find existing limit from database when cache miss")
        void shouldFindExistingLimitFromDatabaseWhenCacheMiss() {
            // Given - 資料已由 @Sql 插入
            given(redisService.getCachedApiLimitConfig(anyString())).willReturn(null);
            given(redisService.isRedisAvailable()).willReturn(true);
            given(redisService.executeRateLimit("test-key-1", 60, 100)).willReturn(5L);
            given(redisService.getTtl("test-key-1")).willReturn(45L);

            // When
            CheckResponse result = rateLimitService.checkApiAccess("test-key-1");

            // Then
            assertThat(result.isAllowed()).isTrue();
            assertThat(result.getCurrentCount()).isEqualTo(5);
            assertThat(result.getLimitCount()).isEqualTo(100);
        }

        @Test
        @DisplayName("Should get usage from database config")
        void shouldGetUsageFromDatabaseConfig() {
            // Given - 資料已由 @Sql 插入
            given(redisService.getCachedApiLimitConfig(anyString())).willReturn(null);
            given(redisService.getCurrentCount("test-key-2")).willReturn(25L);
            given(redisService.getTtl("test-key-2")).willReturn(20L);

            // When
            UsageResponse result = rateLimitService.getUsage("test-key-2");

            // Then
            assertThat(result.getApiKey()).isEqualTo("test-key-2");
            assertThat(result.getCurrentCount()).isEqualTo(25);
            assertThat(result.getLimitCount()).isEqualTo(50);
            assertThat(result.getRemaining()).isEqualTo(25);
            assertThat(result.getWindowSeconds()).isEqualTo(30);
        }

        @Test
        @DisplayName("Should block request when rate limit exceeded")
        void shouldBlockRequestWhenRateLimitExceeded() {
            // Given - 資料已由 @Sql 插入
            given(redisService.getCachedApiLimitConfig(anyString())).willReturn(null);
            given(redisService.isRedisAvailable()).willReturn(true);
            given(redisService.executeRateLimit("test-key-2", 30, 50)).willReturn(60L);
            given(redisService.getTtl("test-key-2")).willReturn(15L);

            // When
            CheckResponse result = rateLimitService.checkApiAccess("test-key-2");

            // Then
            assertThat(result.isAllowed()).isFalse();
            assertThat(result.getMessage()).isEqualTo("Rate limit exceeded");
            assertThat(result.getCurrentCount()).isEqualTo(60);
            assertThat(result.getLimitCount()).isEqualTo(50);
        }
    }

    // ========== 刪除測試 ==========

    @Nested
    @DisplayName("Delete Limit Tests")
    @SqlGroup({
            @Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD),
            @Sql(scripts = "/sql/insert-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD),
            @Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    })
    class DeleteLimitTests {

        @Test
        @DisplayName("Should remove limit from database")
        void shouldRemoveLimitFromDatabase() {
            // Given - 資料已由 @Sql 插入
            assertThat(apiLimitRepository.existsByApiKey("test-key-1")).isTrue();

            // When
            rateLimitService.removeLimit("test-key-1");

            // Then
            assertThat(apiLimitRepository.existsByApiKey("test-key-1")).isFalse();
        }

        @Test
        @DisplayName("Should throw exception when removing non-existent key")
        void shouldThrowExceptionWhenRemovingNonExistentKey() {
            // When & Then
            assertThatThrownBy(() -> rateLimitService.removeLimit("non-existent-key"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("API key not found");
        }
    }

    // ========== 分頁查詢測試 ==========

    @Nested
    @DisplayName("Pagination Tests")
    @SqlGroup({
            @Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD),
            @Sql(scripts = "/sql/insert-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD),
            @Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    })
    class PaginationTests {

        @Test
        @DisplayName("Should get all limits with pagination from database")
        void shouldGetAllLimitsWithPaginationFromDatabase() {
            // Given - 資料已由 @Sql 插入 (3 筆)

            // When
            LimitsResponse result = rateLimitService.getAllLimits(0, 2);

            // Then
            assertThat(result.getLimits()).hasSize(2);
            assertThat(result.getTotalElements()).isEqualTo(3);
            assertThat(result.getTotalPages()).isEqualTo(2);
            assertThat(result.getCurrentPage()).isEqualTo(0);
            assertThat(result.getPageSize()).isEqualTo(2);
        }

        @Test
        @DisplayName("Should return correct pagination for second page")
        void shouldReturnCorrectPaginationForSecondPage() {
            // Given - 資料已由 @Sql 插入 (3 筆)

            // When
            LimitsResponse result = rateLimitService.getAllLimits(1, 2);

            // Then
            assertThat(result.getLimits()).hasSize(1);  // 第二頁只有 1 筆
            assertThat(result.getCurrentPage()).isEqualTo(1);
            assertThat(result.getTotalPages()).isEqualTo(2);
        }
    }

    // ========== 邊界條件測試 ==========

    @Nested
    @DisplayName("Edge Case Tests")
    @Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    class EdgeCaseTests {

        @Test
        @DisplayName("Should allow request when no limit configured in database")
        void shouldAllowRequestWhenNoLimitConfiguredInDatabase() {
            // Given - 資料庫是空的
            given(redisService.getCachedApiLimitConfig(anyString())).willReturn(null);

            // When
            CheckResponse result = rateLimitService.checkApiAccess("no-config-key");

            // Then
            assertThat(result.isAllowed()).isTrue();
            assertThat(result.getMessage()).isEqualTo("No rate limit configured for this API key");
        }

        @Test
        @DisplayName("Should throw exception when getting usage for non-existent key")
        void shouldThrowExceptionWhenGettingUsageForNonExistentKey() {
            // Given - 資料庫是空的
            given(redisService.getCachedApiLimitConfig(anyString())).willReturn(null);

            // When & Then
            assertThatThrownBy(() -> rateLimitService.getUsage("unknown-key"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Failed to get usage information");
        }
    }
}
