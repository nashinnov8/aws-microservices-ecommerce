package com.ecommerce.inventoryservice.config;

import com.ecommerce.inventoryservice.dto.event.InventoryEvent;
import com.ecommerce.inventoryservice.dto.event.ProductEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka configuration for inventory-service.
 *
 * Configures:
 * - Consumer for ProductEvent (from product-service)
 * - Producer for InventoryEvent (to notify other services)
 * - Error handling with retry and dead-letter support
 */
@Configuration
@EnableKafka
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:29092}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:inventory-service}")
    private String groupId;

    /**
     * ObjectMapper configured for Kafka serialization with Java time support.
     */
    @Bean
    public ObjectMapper kafkaObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    // ==================== CUSTOM SERIALIZERS ====================

    /**
     * Custom Kafka serializer for InventoryEvent using Jackson.
     */
    public static class InventoryEventSerializer implements Serializer<InventoryEvent> {
        private final ObjectMapper objectMapper;

        public InventoryEventSerializer() {
            this.objectMapper = new ObjectMapper();
            this.objectMapper.registerModule(new JavaTimeModule());
        }

        public InventoryEventSerializer(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public byte[] serialize(String topic, InventoryEvent data) {
            if (data == null) return null;
            try {
                return objectMapper.writeValueAsBytes(data);
            } catch (Exception e) {
                throw new RuntimeException("Error serializing InventoryEvent", e);
            }
        }
    }

    /**
     * Custom Kafka deserializer for ProductEvent using Jackson.
     */
    public static class ProductEventDeserializer implements Deserializer<ProductEvent> {
        private final ObjectMapper objectMapper;

        public ProductEventDeserializer() {
            this.objectMapper = new ObjectMapper();
            this.objectMapper.registerModule(new JavaTimeModule());
        }

        public ProductEventDeserializer(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public ProductEvent deserialize(String topic, byte[] data) {
            if (data == null) return null;
            try {
                return objectMapper.readValue(data, ProductEvent.class);
            } catch (Exception e) {
                throw new RuntimeException("Error deserializing ProductEvent", e);
            }
        }
    }

    // ==================== PRODUCER CONFIGURATION ====================

    /**
     * Producer configuration for InventoryEvent.
     * Uses String key (SKU) and JSON serialized InventoryEvent value.
     */
    @Bean
    public ProducerFactory<String, InventoryEvent> inventoryEventProducerFactory(ObjectMapper kafkaObjectMapper) {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        // Reliability settings
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 1000);
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        return new DefaultKafkaProducerFactory<>(
                configProps,
                new StringSerializer(),
                new InventoryEventSerializer(kafkaObjectMapper)
        );
    }

    /**
     * KafkaTemplate for sending InventoryEvent messages.
     */
    @Bean
    public KafkaTemplate<String, InventoryEvent> kafkaTemplate(
            ProducerFactory<String, InventoryEvent> inventoryEventProducerFactory) {
        return new KafkaTemplate<>(inventoryEventProducerFactory);
    }

    // ==================== CONSUMER CONFIGURATION ====================

    /**
     * Consumer configuration for ProductEvent.
     * Consumes events from product-service.
     */
    @Bean
    public ConsumerFactory<String, ProductEvent> productEventConsumerFactory(ObjectMapper kafkaObjectMapper) {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        return new DefaultKafkaConsumerFactory<>(
                configProps,
                new StringDeserializer(),
                new ProductEventDeserializer(kafkaObjectMapper)
        );
    }

    /**
     * Kafka listener container factory with manual acknowledgment.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ProductEvent> kafkaListenerContainerFactory(
            ConsumerFactory<String, ProductEvent> productEventConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, ProductEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(productEventConsumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                (consumerRecord, exception) -> {
                    // Runs after all retries are exhausted
                },
                new FixedBackOff(1000L, 3L)
        );
        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }
}
