package com.example.demo.config;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RocketMqConfig {
    
    @Value("${rocketmq.name-server}")
    private String nameServer;
    
    @Value("${rocketmq.producer.group}")
    private String producerGroup;
    
    @Bean
    public DefaultMQProducer defaultMQProducer() {
        DefaultMQProducer producer = new DefaultMQProducer();
        producer.setProducerGroup(producerGroup);
        producer.setNamesrvAddr(nameServer);
        producer.setSendMsgTimeout(10000); // 增加超時時間到10秒
        producer.setRetryTimesWhenSendFailed(0); // 不重試，快速失敗
        producer.setMaxMessageSize(1024 * 4); // 設置最大消息大小
        
        try {
            producer.start();
        } catch (Exception e) {
            throw new RuntimeException("Failed to start RocketMQ producer", e);
        }
        
        return producer;
    }
}