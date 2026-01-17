#!/bin/bash

API_KEY="debug-test-key"
BASE_URL="http://localhost:8080"

echo "🧪 Rate Limit Testing Script"
echo "=============================="

# 1. Create rate limit
echo "📝 1. Creating rate limit rule..."
curl -s -X POST "${BASE_URL}/limits" \
  -H "Content-Type: application/json" \
  -d "{\"apiKey\":\"${API_KEY}\",\"limit\":5,\"windowSeconds\":60}" | jq .

echo -e "\n⏳ Waiting 2 seconds...\n"
sleep 2

# 2. Test 6 requests
echo "🚀 2. Testing 6 consecutive requests..."
for i in {1..6}; do
  echo "Request #${i}:"
  response=$(curl -s "${BASE_URL}/check?apiKey=${API_KEY}")
  echo "$response" | jq .
  
  # Check Redis value
  redis_count=$(docker exec redis redis-cli GET "rate:limit:cnt:${API_KEY}" 2>/dev/null || echo "null")
  echo "Redis count: $redis_count"
  echo "---"
  sleep 1
done

echo "🔍 3. Final Redis state:"
docker exec redis redis-cli KEYS "*" | grep "$API_KEY"
docker exec redis redis-cli GET "rate:limit:cnt:${API_KEY}"