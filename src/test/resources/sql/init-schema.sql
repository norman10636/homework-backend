-- 初始化表結構
DROP TABLE IF EXISTS api_limits;

CREATE TABLE api_limits (
    api_key VARCHAR(255) NOT NULL PRIMARY KEY,
    limit_count INT NOT NULL,
    window_seconds INT NOT NULL,
    created_at DATETIME(6),
    updated_at DATETIME(6)
);
