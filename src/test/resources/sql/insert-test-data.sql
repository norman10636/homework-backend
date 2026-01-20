-- 插入測試資料
INSERT INTO api_limits (api_key, limit_count, window_seconds, created_at, updated_at)
VALUES
    ('test-key-1', 100, 60, NOW(), NOW()),
    ('test-key-2', 50, 30, NOW(), NOW()),
    ('test-key-3', 200, 120, NOW(), NOW());
