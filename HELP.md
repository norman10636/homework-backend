# Rate Limiting Service - Setup Guide

## Quick Start

### 1. Start Infrastructure Services
```bash
docker-compose up -d
```
This will start:
- MySQL (port 3306)
- Redis (port 6379) 
- RocketMQ NameServer (port 9876)
- RocketMQ Broker (port 10911)
- RocketMQ Console (port 8088)

### 2. Run the Application
```bash
./mvnw spring-boot:run
```
The service will start on port 8080.

## API Endpoints

### Create Rate Limit
```bash
POST /limits
Content-Type: application/json

{
  "apiKey": "abc-123",
  "limit": 100,
  "windowSeconds": 60
}
```

### Check API Access
```bash
GET /check?apiKey=abc-123
```
Response:
```json
{
  "allowed": true,
  "message": "Request allowed",
  "currentCount": 1,
  "limitCount": 100,
  "remainingTtl": 59
}
```

### Get Usage Statistics
```bash
GET /usage?apiKey=abc-123
```

### Remove Rate Limit
```bash
DELETE /limits/abc-123
```

### List All Limits (with pagination)
```bash
GET /limits?page=0&size=10
```

### Health Check
```bash
GET /health
```

## Key Features Implemented

 **Fixed Window Rate Limiting** with Redis atomic operations  
 **Cache-aside Pattern** for configuration caching  
 **Fail-open Strategy** when Redis is unavailable  
 **Lua Script** for atomic INCR + EXPIRE operations  
 **RocketMQ Integration** for async event logging  
 **MySQL Persistence** for rate limit configurations  
 **Pagination Support** for listing limits  
 **Proper Error Handling** with meaningful status codes  

## Architecture

- **Controller Layer**: REST API endpoints
- **Service Layer**: Business logic with Cache-aside Pattern
- **Repository Layer**: JPA data access 
- **Redis Layer**: Atomic rate limiting + configuration cache
- **MQ Layer**: Async event publishing for blocked requests

## Testing

Run tests:
```bash
./mvnw test
```

## Technical Highlights

1. **Atomic Rate Limiting**: Uses Lua script to ensure INCR + EXPIRE atomicity
2. **Cache Strategy**: Cache-aside pattern with 5-minute TTL for configs
3. **Resilience**: Fail-open strategy when Redis is unavailable
4. **Async Logging**: RocketMQ publishes blocked/config events asynchronously
5. **Database Design**: Optimized schema with proper indexing

## RocketMQ Console

Access the RocketMQ Console at: http://localhost:8088

Topics:
- `rate-limit-events`: Contains blocked requests and config changes