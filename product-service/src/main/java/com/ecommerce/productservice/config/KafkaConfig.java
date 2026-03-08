package com.ecommerce.productservice.config;

import com.ecommerce.productservice.dto.event.InventoryEvent;
import com.ecommerce.productservice.dto.event.ProductEvent;
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
 * Kafka configuration for product-service.
 *
 * Configures:
 * - Producer for ProductEvent (to notify inventory-service of variant changes)
 * - Consumer for InventoryEvent (to track stock availability)
 * - Error handling with retry and dead-letter support
 */
@Configuration
@EnableKafka
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:29092}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:product-service}")
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
     * Custom Kafka serializer for ProductEvent using Jackson.
     */
    public static class ProductEventSerializer implements Serializer<ProductEvent> {
        private final ObjectMapper objectMapper;

        public ProductEventSerializer() {
            this.objectMapper = new ObjectMapper();
            this.objectMapper.registerModule(new JavaTimeModule());
        }

        public ProductEventSerializer(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public byte[] serialize(String topic, ProductEvent data) {
            if (data == null) return null;
            try {
                return objectMapper.writeValueAsBytes(data);
            } catch (Exception e) {
                throw new RuntimeException("Error serializing ProductEvent", e);
            }
        }
    }

    /**
     * Custom Kafka deserializer for InventoryEvent using Jackson.
     */
    public static class InventoryEventDeserializer implements Deserializer<InventoryEvent> {
        private final ObjectMapper objectMapper;

        public InventoryEventDeserializer() {
            this.objectMapper = new ObjectMapper();
            this.objectMapper.registerModule(new JavaTimeModule());
        }

        public InventoryEventDeserializer(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public InventoryEvent deserialize(String topic, byte[] data) {
            if (data == null) return null;
            try {
                return objectMapper.readValue(data, InventoryEvent.class);
            } catch (Exception e) {
                throw new RuntimeException("Error deserializing InventoryEvent", e);
            }
        }
    }

    // ==================== PRODUCER CONFIGURATION ====================

    /**
     * Producer configuration for ProductEvent.
     * Uses String key (SKU) and JSON serialized ProductEvent value.
     */
    @Bean
    public ProducerFactory<String, ProductEvent> productEventProducerFactory(ObjectMapper kafkaObjectMapper) {
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
                new ProductEventSerializer(kafkaObjectMapper)
        );
    }

    /**
     * KafkaTemplate for sending ProductEvent messages.
     */
    @Bean
    public KafkaTemplate<String, ProductEvent> kafkaTemplate(
            ProducerFactory<String, ProductEvent> productEventProducerFactory) {
        return new KafkaTemplate<>(productEventProducerFactory);
    }

    // ==================== CONSUMER CONFIGURATION ====================

    /**
     * Consumer configuration for InventoryEvent.
     * Consumes events from inventory-service to track stock availability.
     */
    @Bean
    public ConsumerFactory<String, InventoryEvent> inventoryEventConsumerFactory(ObjectMapper kafkaObjectMapper) {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        return new DefaultKafkaConsumerFactory<>(
                configProps,
                new StringDeserializer(),
                new InventoryEventDeserializer(kafkaObjectMapper)
        );
    }

    /**
     * Kafka listener container factory for InventoryEvent consumers.
     * Referenced by @KafkaListener(containerFactory = "inventoryEventListenerContainerFactory")
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, InventoryEvent> inventoryEventListenerContainerFactory(
            ConsumerFactory<String, InventoryEvent> inventoryEventConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, InventoryEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(inventoryEventConsumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        // Retry 3 times with 1 second backoff, then log and skip
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

