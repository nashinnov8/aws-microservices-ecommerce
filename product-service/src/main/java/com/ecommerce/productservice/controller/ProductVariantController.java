package com.ecommerce.productservice.controller;

import com.ecommerce.productservice.dto.producvariant.ProductVariantRequest;
import com.ecommerce.productservice.dto.producvariant.ProductVariantResponse;
import com.ecommerce.productservice.service.ProductVariantService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import response.ApiResponse;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/products/{productId}/variants")
public class ProductVariantController {
    private final ProductVariantService productVariantService;

    public ProductVariantController(ProductVariantService productVariantService) {
        this.productVariantService = productVariantService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProductVariantResponse>> createVariant(
            @PathVariable UUID productId,
            @Valid @RequestBody ProductVariantRequest request) {
        ProductVariantResponse response = productVariantService.create(productId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("201", "Product variant created successfully", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProductVariantResponse>>> getAllVariantsByProductId(@PathVariable UUID productId) {
        List<ProductVariantResponse> response = productVariantService.getAllByProductId(productId);
        return ResponseEntity.ok(ApiResponse.success("200", "Product variants retrieved successfully", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductVariantResponse>> getVariantById(
            @PathVariable UUID productId,
            @PathVariable UUID id) {
        ProductVariantResponse response = productVariantService.getById(id);
        return ResponseEntity.ok(ApiResponse.success("200", "Product variant retrieved successfully", response));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductVariantResponse>> updateVariant(
            @PathVariable UUID productId,
            @PathVariable UUID id,
            @Valid @RequestBody ProductVariantRequest request) {
        ProductVariantResponse response = productVariantService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success("200", "Product variant updated successfully", response));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteVariant(
            @PathVariable UUID productId,
            @PathVariable UUID id) {
        productVariantService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("200", "Product variant deleted successfully", null));
    }
}
