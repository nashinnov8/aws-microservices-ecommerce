package com.ecommerce.productservice.service;

import com.ecommerce.productservice.domain.entity.Brand;
import com.ecommerce.productservice.domain.entity.Category;
import com.ecommerce.productservice.domain.entity.Product;
import com.ecommerce.productservice.domain.enums.ProductStatus;
import com.ecommerce.productservice.domain.repository.BrandRepository;
import com.ecommerce.productservice.domain.repository.CategoryRepository;
import com.ecommerce.productservice.domain.repository.ProductRepository;
import com.ecommerce.productservice.dto.product.GetProductRequest;
import com.ecommerce.productservice.dto.product.ProductRequest;
import com.ecommerce.productservice.dto.product.ProductResponse;
import com.ecommerce.productservice.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import response.PageResponse;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class ProductService {
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;

    public ProductService(ProductRepository productRepository, CategoryRepository categoryRepository, BrandRepository brandRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        log.info("Creating product with name: {} and SKU: {}", request.name(), request.baseSku());

        Product product = new Product();
        product.setName(request.name());
        product.setDescription(request.description());
        product.setBaseSku(request.baseSku());
        product.setPrice(request.price());
        product.setImageUrl(request.imageUrl());
        product.setStatus(ProductStatus.ACTIVE);

        if (request.categoryId() != null) {
            Category category = categoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + request.categoryId()));
            product.setCategory(category);
        }

        if (request.brandId() != null) {
            Brand brand = brandRepository.findById(request.brandId())
                    .orElseThrow(() -> new ResourceNotFoundException("Brand not found with id: " + request.brandId()));
            product.setBrand(brand);
        }

        Product savedProduct = productRepository.save(product);
        log.info("Product created successfully with id: {}", savedProduct.getId());
        return ProductResponse.fromEntity(savedProduct);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> getAllProducts(GetProductRequest request) {
        Integer pageNumber = request.pageNumber();
        Integer pageSize = request.pageSize();

        Pageable pageable = PageRequest.of(pageNumber - 1, pageSize);
        Page<Product> productPage = productRepository.findAll(pageable);

        List<ProductResponse> products = productPage.getContent()
                .stream()
                .map(ProductResponse::fromEntity)
                .toList();
        return new PageResponse<>(
                products,
                productPage.getNumber() + 1,
                productPage.getSize(),
                productPage.getTotalElements(),
                productPage.getTotalPages(),
                productPage.isLast()
        );
    }

    @Transactional(readOnly = true)
    public ProductResponse getById(UUID id) {
        log.debug("Fetching product with id: {}", id);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        return ProductResponse.fromEntity(product);
    }

    @Transactional
    public ProductResponse update(UUID id, ProductRequest request) {
        log.info("Updating product with id: {}", id);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        product.setName(request.name());
        product.setDescription(request.description());
        product.setBaseSku(request.baseSku());
        product.setPrice(request.price());
        product.setImageUrl(request.imageUrl());

        if (request.categoryId() != null) {
            Category category = categoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + request.categoryId()));
            product.setCategory(category);
        } else {
            product.setCategory(null);
        }

        // Update brand
        if (request.brandId() != null) {
            Brand brand = brandRepository.findById(request.brandId())
                    .orElseThrow(() -> new ResourceNotFoundException("Brand not found with id: " + request.brandId()));
            product.setBrand(brand);
        } else {
            product.setBrand(null);
        }

        Product updatedProduct = productRepository.save(product);
        log.info("Product updated successfully with id: {}", id);
        return ProductResponse.fromEntity(updatedProduct);
    }

    @Transactional
    public void delete(UUID id) {
        log.info("Deleting product with id: {}", id);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        productRepository.delete(product);
        log.info("Product deleted successfully with id: {}", id);
    }
}
