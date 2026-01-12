CREATE TABLE IF NOT EXISTS api_limits (
    api_key VARCHAR(255) PRIMARY KEY,
    limit_count INT NOT NULL,
    window_seconds INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE INDEX idx_api_limits_created_at ON api_limits(created_at);