package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {
    
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringRedisSerializer);
        template.setHashKeySerializer(stringRedisSerializer);
        
        GenericJackson2JsonRedisSerializer jsonRedisSerializer = new GenericJackson2JsonRedisSerializer();
        template.setValueSerializer(jsonRedisSerializer);
        template.setHashValueSerializer(jsonRedisSerializer);
        
        template.afterPropertiesSet();
        return template;
    }
    
    @Bean("counterRedisTemplate")
    public RedisTemplate<String, String> counterRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringRedisSerializer);
        template.setValueSerializer(stringRedisSerializer);
        template.setHashKeySerializer(stringRedisSerializer);
        template.setHashValueSerializer(stringRedisSerializer);
        
        template.afterPropertiesSet();
        return template;
    }
    
    @Bean
    public DefaultRedisScript<Long> rateLimitScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(
            """
            local key = KEYS[1]
            local window_seconds = tonumber(ARGV[1])
            local limit = tonumber(ARGV[2])
            
            local current = redis.call('GET', key)
            if current == false then
                redis.call('SET', key, 1)
                redis.call('EXPIRE', key, window_seconds)
                return 1
            else
                local count = tonumber(current)
                local new_count = redis.call('INCR', key)
                return new_count
            end
            """
        );
        script.setResultType(Long.class);
        return script;
    }
    
    @Bean
    public DefaultRedisScript<Long> getCurrentCountScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(
            """
            local key = KEYS[1]
            local current = redis.call('GET', key)
            if current == false then
                return 0
            else
                return tonumber(current)
            end
            """
        );
        script.setResultType(Long.class);
        return script;
    }
}