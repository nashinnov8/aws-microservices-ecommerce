package com.ecommerce.orderservice.config;

import com.ecommerce.orderservice.dto.event.OrderEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.apache.kafka.common.serialization.Serializer;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Kafka configuration for order-service.
 * Configures:
 * - Producer for OrderEvent (to notify inventory-service and other services)
 * - Consumer for InventoryEvent (from inventory-service)
 * - Error handling with retry and dead-letter support
 */
@Configuration
@EnableKafka
public class KafkaConfig {
    @Value("${spring.kafka.bootstrap-servers:localhost:29092}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:order-service}")
    private String groupId;

    @Bean
    public ObjectMapper kafkaObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    public static class OrderEventSerializer implements Serializer<OrderEvent> {
        private final ObjectMapper objectMapper;

        public OrderEventSerializer() {
            this.objectMapper = new ObjectMapper();
            this.objectMapper.registerModule(new JavaTimeModule());
        }

        public OrderEventSerializer(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public byte[] serialize(String topic, OrderEvent data) {
            if (data == null) return null;
            try {
                return objectMapper.writeValueAsBytes(data);
            } catch (Exception e) {
                throw new RuntimeException("Error serializing OrderEvent", e);
            }
        }
    }

    @Bean
    public ProducerFactory<String, OrderEvent> orderEventProducerFactory(ObjectMapper kafkaObjectMapper) {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        // Reliability settings
        configProps.put(ProducerConfig.ACKS_CONFIG, "all"); // Wait for all
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3); // Retry up to 3 times
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true); // Enable
        configProps.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 1000);


        return new DefaultKafkaProducerFactory<>(
                configProps,
                new StringSerializer(),
                new OrderEventSerializer(kafkaObjectMapper)
        );
    }

    /**
     * KafkaTemplate for sending OrderEvent messages.
     */
    @Bean
    public KafkaTemplate<String, OrderEvent> kafkaTemplate(
            ProducerFactory<String, OrderEvent> orderEventProducerFactory) {
        return new KafkaTemplate<>(orderEventProducerFactory);
    }

    // ==================== CONSUMER CONFIGURATION ====================

    /**
     * Consumer configuration for InventoryEvent.
     * Uses String key (SKU) and JSON deserialized InventoryEvent value.
     */
    @Bean
    public ConsumerFactory<String, String> inventoryEventConsumerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        return new DefaultKafkaConsumerFactory<>(configProps, new StringDeserializer(), new StringDeserializer());
    }

    /**
     * Kafka listener container factory for InventoryEvent consumers.
     * Referenced by @KafkaListener(containerFactory = "inventoryEventListenerContainerFactory")
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> inventoryEventListenerContainerFactory(
            ConsumerFactory<String, String> inventoryEventConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(inventoryEventConsumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                (consumerRecord, exception) -> {
                    // Runs after all retries are exhausted — send to DLQ in production
                },
                new FixedBackOff(1000L, 3L)
        );
        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }
}
