package com.example.demo.config;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.MySQLContainer;

/**
 * MySQL Testcontainer 配置
 * 提供可重用的 MySQL 容器配置，用於整合測試
 *
 * 使用方式:
 * <pre>
 * {@code
 * @SpringBootTest
 * @Testcontainers
 * @ContextConfiguration(initializers = MySQLTestContainerConfig.Initializer.class)
 * class YourIntegrationTest {
 *     // 測試程式碼
 * }
 * }
 * </pre>
 */
public class MySQLTestContainerConfig {

    private static final String MYSQL_IMAGE = "mysql:8.0";
    private static final String DATABASE_NAME = "testdb";
    private static final String USERNAME = "testuser";
    private static final String PASSWORD = "testpass";

    // 使用 Singleton 模式，確保整個測試過程只啟動一個容器
    private static final MySQLContainer<?> MYSQL_CONTAINER;

    static {
        MYSQL_CONTAINER = new MySQLContainer<>(MYSQL_IMAGE)
                .withDatabaseName(DATABASE_NAME)
                .withUsername(USERNAME)
                .withPassword(PASSWORD)
                .withReuse(true);  // 啟用容器重用，加速測試
        MYSQL_CONTAINER.start();
    }

    public static MySQLContainer<?> getContainer() {
        return MYSQL_CONTAINER;
    }

    /**
     * Spring ApplicationContext Initializer
     * 用於動態設定 DataSource 屬性
     */
    public static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext context) {
            TestPropertyValues.of(
                    "spring.datasource.url=" + MYSQL_CONTAINER.getJdbcUrl(),
                    "spring.datasource.username=" + MYSQL_CONTAINER.getUsername(),
                    "spring.datasource.password=" + MYSQL_CONTAINER.getPassword(),
                    "spring.jpa.hibernate.ddl-auto=none"
            ).applyTo(context.getEnvironment());
        }
    }
}
