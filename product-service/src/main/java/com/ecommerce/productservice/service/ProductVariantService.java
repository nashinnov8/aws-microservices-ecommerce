package com.ecommerce.productservice.service;

import com.ecommerce.productservice.domain.entity.Product;
import com.ecommerce.productservice.domain.entity.ProductVariant;
import com.ecommerce.productservice.domain.enums.ProductStatus;
import com.ecommerce.productservice.domain.repository.ProductRepository;
import com.ecommerce.productservice.domain.repository.ProductVariantRepository;
import com.ecommerce.productservice.dto.producvariant.ProductVariantRequest;
import com.ecommerce.productservice.dto.producvariant.ProductVariantResponse;
import com.ecommerce.productservice.exception.ResourceNotFoundException;
import com.ecommerce.productservice.kafka.ProductEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class ProductVariantService {
    private final ProductVariantRepository productVariantRepository;
    private final ProductRepository productRepository;
    private final ProductEventProducer productEventProducer;

    @Transactional
    public ProductVariantResponse create(UUID productId, ProductVariantRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        ProductVariant variant = new ProductVariant();
        variant.setVariantSku(request.variantSku());
        variant.setProduct(product);
        variant.setSize(request.size());
        variant.setColor(request.color());
        variant.setMaterial(request.material());
        variant.setPriceAdjustment(request.priceAdjustment());
        variant.setImageUrl(request.imageUrl());
        variant.setStatus(ProductStatus.ACTIVE);

        ProductVariant savedVariant = productVariantRepository.save(variant);
        log.info("Created variant {} (SKU: {}) for product {}", savedVariant.getId(), savedVariant.getVariantSku(), productId);

        // Publish VARIANT_CREATED event → inventory-service auto-creates inventory record
        productEventProducer.publishVariantCreated(savedVariant);

        return ProductVariantResponse.fromEntity(savedVariant);
    }

    @Transactional(readOnly = true)
    public List<ProductVariantResponse> getAllByProductId(UUID productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        return product.getVariants().stream()
                .map(ProductVariantResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductVariantResponse getById(UUID id) {
        ProductVariant variant = productVariantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product variant not found with id: " + id));
        return ProductVariantResponse.fromEntity(variant);
    }

    @Transactional
    public ProductVariantResponse update(UUID id, ProductVariantRequest request) {
        ProductVariant variant = productVariantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product variant not found with id: " + id));

        variant.setVariantSku(request.variantSku());
        variant.setSize(request.size());
        variant.setColor(request.color());
        variant.setMaterial(request.material());
        variant.setPriceAdjustment(request.priceAdjustment());
        variant.setImageUrl(request.imageUrl());

        ProductVariant updatedVariant = productVariantRepository.save(variant);
        log.info("Updated variant {} (SKU: {})", updatedVariant.getId(), updatedVariant.getVariantSku());

        // Publish VARIANT_UPDATED event → inventory-service updates metadata
        productEventProducer.publishVariantUpdated(updatedVariant);

        return ProductVariantResponse.fromEntity(updatedVariant);
    }

    @Transactional
    public void delete(UUID id) {
        ProductVariant variant = productVariantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product variant not found with id: " + id));

        log.info("Deleting variant {} (SKU: {})", variant.getId(), variant.getVariantSku());

        // Publish VARIANT_DELETED event BEFORE deletion → inventory-service deactivates record
        productEventProducer.publishVariantDeleted(variant);

        productVariantRepository.delete(variant);
    }
}
