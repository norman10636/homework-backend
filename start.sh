#!/bin/bash

echo "🚀 Starting Rate Limiter Service..."

# 設置JAVA_HOME
export JAVA_HOME=$(/usr/libexec/java_home -v 17)

# 1. 啟動基礎設施
echo "📦 Starting infrastructure services..."
docker-compose up -d

# 2. 等待MySQL啟動
echo "⏳ Waiting for MySQL to be ready..."
for i in {1..30}; do
  if docker exec mysql mysqladmin ping -h localhost --silent 2>/dev/null; then
    echo "✅ MySQL is ready!"
    break
  fi
  echo "   Waiting for MySQL... ($i/30)"
  sleep 2
done

# 3. 等待Redis啟動
echo "⏳ Waiting for Redis to be ready..."
until docker exec redis redis-cli ping > /dev/null 2>&1; do
  echo "   Waiting for Redis..."
  sleep 1
done
echo "✅ Redis is ready!"

# 4. 檢查服務狀態
echo "📊 Service status:"
docker-compose ps

# 5. 啟動Spring Boot應用
echo "🌟 Starting Spring Boot application..."
echo "📝 Application will start at http://localhost:8080"
echo "📋 Health check: http://localhost:8080/health"
echo "🎮 RocketMQ Console: http://localhost:8088"
echo ""

./mvnw spring-boot:run