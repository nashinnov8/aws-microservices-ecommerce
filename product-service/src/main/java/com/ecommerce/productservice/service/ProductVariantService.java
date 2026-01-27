package com.ecommerce.productservice.service;

import com.ecommerce.productservice.domain.entity.Product;
import com.ecommerce.productservice.domain.entity.ProductVariant;
import com.ecommerce.productservice.domain.enums.ProductStatus;
import com.ecommerce.productservice.domain.repository.ProductRepository;
import com.ecommerce.productservice.domain.repository.ProductVariantRepository;
import com.ecommerce.productservice.dto.producvariant.ProductVariantRequest;
import com.ecommerce.productservice.dto.producvariant.ProductVariantResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ProductVariantService {
    private final ProductVariantRepository productVariantRepository;
    private final ProductRepository productRepository;

    public ProductVariantService(ProductVariantRepository productVariantRepository, ProductRepository productRepository) {
        this.productVariantRepository = productVariantRepository;
        this.productRepository = productRepository;
    }

    @Transactional
    public ProductVariantResponse create(UUID productId, ProductVariantRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + productId));

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
        return ProductVariantResponse.fromEntity(savedVariant);
    }

    @Transactional(readOnly = true)
    public List<ProductVariantResponse> getAllByProductId(UUID productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + productId));

        return product.getVariants().stream()
                .map(ProductVariantResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductVariantResponse getById(UUID id) {
        ProductVariant variant = productVariantRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product variant not found with id: " + id));
        return ProductVariantResponse.fromEntity(variant);
    }

    @Transactional
    public ProductVariantResponse update(UUID id, ProductVariantRequest request) {
        ProductVariant variant = productVariantRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product variant not found with id: " + id));

        variant.setVariantSku(request.variantSku());
        variant.setSize(request.size());
        variant.setColor(request.color());
        variant.setMaterial(request.material());
        variant.setPriceAdjustment(request.priceAdjustment());
        variant.setImageUrl(request.imageUrl());

        ProductVariant updatedVariant = productVariantRepository.save(variant);
        return ProductVariantResponse.fromEntity(updatedVariant);
    }

    @Transactional
    public void delete(UUID id) {
        ProductVariant variant = productVariantRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product variant not found with id: " + id));
        productVariantRepository.delete(variant);
    }
}
