package com.ecommerce.inventoryservice.controller;

import com.ecommerce.inventoryservice.dto.inventory.*;
import com.ecommerce.inventoryservice.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import response.ApiResponse;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@Slf4j
public class InventoryController {
    private final InventoryService inventoryService;

    /**
     * Get stock information for a product variant by SKU.
     * @param sku sku Primary business identifier for inventory operations, derived from ProductVariant.variantSku in product-service.
     * @return stock information including current stock level, reserved stock, and availability status.
     */
    @GetMapping("/{sku}")
    public ResponseEntity<ApiResponse<StockInfoResponse>> getStockInfo(@PathVariable String sku) {
        log.info("Received request for stock info of SKU: {}", sku);
        StockInfoResponse response = inventoryService.getStockBySku(sku);
        return ResponseEntity.ok(
                ApiResponse.success("200", "Stock information retrieved successfully", response)
        );
    }

    @GetMapping("/product/{productId}")
    public ResponseEntity<ApiResponse<List<StockInfoResponse>>> getStocksByProductId(@PathVariable UUID productId) {
        log.info("Received request for stock information of product ID: {}", productId);
        List<StockInfoResponse> responses = inventoryService.getStocksByProductId(productId);
        return  ResponseEntity.ok(
                ApiResponse.success("200", "Stock information retrieved successfully", responses)
        );
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('INVENTORY_MANAGER')")
    public ResponseEntity<ApiResponse<Void>> initializeInventory(
            @Valid @RequestBody CreateInventoryRequest request) {
        log.info("Received request to initialize inventory for SKU: {}", request.sku());
        inventoryService.initializeInventory(
                request.sku(),
                request.variantId(),
                request.productId(),
                request.productName(),
                request.variantName(),
                request.initialStock()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success("201", "Inventory initialized successfully", null)
        );
    }

    @PutMapping("/{sku}/stock")
    @PreAuthorize("hasRole('ADMIN') or hasRole('INVENTORY_MANAGER')")
    public ResponseEntity<ApiResponse<StockInfoResponse>> updateStock(
            @PathVariable String sku,
            @Valid @RequestBody UpdateStockRequest request
    ) {
        log.info("Received request to update stock for SKU: {}", sku);
        StockInfoResponse response = inventoryService.updateStock(sku, request);

        return ResponseEntity.ok(
                ApiResponse.success("200", "Stock updated successfully", response)
        );
    }

    @GetMapping("/alerts/low-stock")
    public ResponseEntity<ApiResponse<List<LowStockItemResponse>>> getLowStockItems() {
        log.info("Received request for low stock alerts");
        List<LowStockItemResponse> responses = inventoryService.getLowStockItems();
        return ResponseEntity.ok(
                ApiResponse.success("200", "Low stock alerts retrieved successfully", responses)
        );
    }

    @PostMapping("/check-availability")
    public ResponseEntity<ApiResponse<BulkStockCheckResponse>> checkAvailability(
            @Valid @RequestBody BulkStockCheckRequest request
    ) {
        log.info("Checking availability for {} items", request.items().size());
        BulkStockCheckResponse response = inventoryService.checkAvailability(request);
        return ResponseEntity.ok(
                ApiResponse.success("200", "Availability check completed", response)
        );
    }


}
