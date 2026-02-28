package com.ecommerce.inventoryservice.service;

import com.ecommerce.inventoryservice.domain.entity.Inventory;
import com.ecommerce.inventoryservice.domain.entity.StockMovement;
import com.ecommerce.inventoryservice.domain.enums.MovementType;
import com.ecommerce.inventoryservice.domain.repository.InventoryRepository;
import com.ecommerce.inventoryservice.domain.repository.StockMovementRepository;
import com.ecommerce.inventoryservice.dto.inventory.StockInfoResponse;
import com.ecommerce.inventoryservice.dto.inventory.UpdateStockRequest;
import com.ecommerce.inventoryservice.exception.InventoryNotFoundException;
import com.ecommerce.inventoryservice.kafka.InventoryEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {
    private final InventoryRepository inventoryRepository;
    private final StockMovementRepository stockMovementRepository;
    private final InventoryEventProducer eventProducer;

    /**
     * Initialize inventory for a new product variant.
     *
     * @param sku Primary business identifier (from ProductVariant.variantSku)
     * @param variantId UUID of the variant in product-service
     * @param productId UUID of the product in product-service
     * @param productName Denormalized product name for display
     * @param variantName Denormalized variant name (e.g., "Red / XL")
     * @param initialStock Initial stock quantity (usually 0)
     * @return The inventory record (existing or newly created)
     */
    @Transactional
    public Inventory initializeInventory(
            String sku,
            UUID variantId,
            UUID productId,
            String productName,
            String variantName,
            int initialStock
    ) {
        /**
         * 1. Check if inventory already exists for SKU
         * 2. If exists, return existing inventory
         * 3. If not, create new inventory with initial stock
         */
        Optional<Inventory> inventoryOptional = inventoryRepository.findBySku(sku);

        //Check if inventory already exists for SKU
        if (inventoryOptional.isPresent()) {
            log.info("Inventory already exists for SKU: {}", sku);

            Inventory existingInventory = inventoryOptional.get();
            boolean isMetaDataUpdated = false;

            if (existingInventory.getProductName().equals(productName)) {
                existingInventory.setProductName(productName);
                isMetaDataUpdated = true;
            }

            if (existingInventory.getVariantName().equals(variantName)) {
                existingInventory.setVariantName(variantName);
                isMetaDataUpdated = true;
            }

            if (isMetaDataUpdated) {
                inventoryRepository.save(existingInventory);
                log.info("Updated metadata for existing inventory SKU: {}", sku);
                return existingInventory;
            } else {
                log.info("No metadata changes for existing inventory SKU: {}", sku);
                return existingInventory;
            }
        } else {
            log.info("Creating new inventory for SKU: {}", sku);
            Inventory newInventory = new Inventory(sku, variantId, productId, productName, variantName);
            newInventory.setAvailableStock(initialStock);

            Inventory saved = inventoryRepository.save(newInventory);
            log.info("Created new inventory with ID: {} for SKU: {}", saved.getId(), sku);

            if (initialStock > 0) {
                StockMovement stockMovement = new StockMovement(
                        saved.getId(),
                        MovementType.RECEIVE,
                        initialStock,
                        0,
                        initialStock,
                        "Initial stock setup",
                        "SYSTEM"
                );
                stockMovementRepository.save(stockMovement);
            }

            log.info("Successfully created inventory for SKU: {} with ID: {}", sku, saved.getId());
            return saved;
        }
    }

    /**
     * Get all stock information for a given product ID.
     *
     * @param productId UUID of the product in product-service
     * @return List of StockInfoResponse for all variants of the product
     */
    @Transactional(readOnly = true)
    public List<StockInfoResponse> getStocksByProductId(UUID productId) {
        return inventoryRepository.findByProductIdAndIsActiveTrue(productId)
                .stream()
                .map(this::mapToStockInfoResponse)
                .collect(Collectors.toList());
    }

    /**
     * Update stock quantity for a SKU.
     * Checking for low stock alerts after update.
     *
     * @param sku Item SKU
     * @param request UpdateStockRequest containing operation, quantity, reason, performedBy
     * @return Updated StockInfoResponse
     */
    @Transactional
    public StockInfoResponse updateStock(String sku, UpdateStockRequest request) {
        Inventory inventory = inventoryRepository.findBySkuForUpdate(sku)
                .orElseThrow(() -> new InventoryNotFoundException("Inventory not found for SKU: " + sku));

        int previousStock = inventory.getAvailableStock();
        int newStock;

        // Switch case for update stock based on OPERATION request
        switch (request.operation()) {
            case ADD:
                newStock = previousStock + request.quantity();
                break;
            case SUBTRACT:
                newStock = previousStock - request.quantity();
                break;
            case SET:
                newStock = request.quantity();
                break;
            default:
                throw new IllegalArgumentException("Invalid stock update operation: " + request.operation());
        }

        // Update inventory stock
        inventory.setAvailableStock(newStock);
        Inventory saved = inventoryRepository.save(inventory);

        // Record stock movement type for audit trail
        StockMovement movement = new StockMovement(
                saved.getId(),
                MovementType.ADJUST,
                request.quantity(),
                previousStock,
                newStock,
                request.reason(),
                request.performedBy()
        );
        stockMovementRepository.save(movement);

        // Publish stock updated event
        eventProducer.publishStockUpdated(
                saved.getId(),
                sku,
                saved.getVariantId(),
                saved.getProductId(),
                previousStock,
                newStock,
                saved.getReservedStock(),
                request.reason()
        );

        // Check for low stock alert
        if (saved.isLowStock() && !wasAlreadyLowStock(previousStock, saved.getReorderPoint())) {
            log.warn("Low stock alert for SKU: {}. Available stock: {}", sku, newStock);
            eventProducer.publishLowStockAlert(
                    saved.getId(),
                    sku,
                    saved.getVariantId(),
                    saved.getProductId(),
                    newStock,
                    saved.getReorderPoint()
            );
        }

        // Check for out of stock
        if (newStock == 0 && previousStock > 0) {
            eventProducer.publishOutOfStock(
                    saved.getId(),
                    sku,
                    saved.getVariantId(),
                    saved.getProductId()
            );
        }

        // Check for back in stock
        if (newStock > 0 && previousStock == 0) {
            eventProducer.publishBackInStock(
                    saved.getId(),
                    sku,
                    saved.getVariantId(),
                    saved.getProductId(),
                    newStock
            );
        }

        log.info("Updated stock for SKU: {}. Previous: {}, New: {}", sku, previousStock, newStock);
        return mapToStockInfoResponse(saved);
    }



    private StockInfoResponse mapToStockInfoResponse(Inventory inventory) {
        return new StockInfoResponse(
                inventory.getId(),
                inventory.getSku(),
                inventory.getVariantId(),
                inventory.getProductId(),
                inventory.getProductName(),
                inventory.getVariantName(),
                inventory.getAvailableStock(),
                inventory.getReservedStock(),
                inventory.getTotalStock(),
                inventory.isLowStock(),
                inventory.getReorderPoint(),
                inventory.getMinStockLevel(),
                inventory.getMaxStockLevel(),
                inventory.isActive(),
                inventory.getCreatedAt(),
                inventory.getUpdatedAt()
        );
    }

    private boolean wasAlreadyLowStock(int previousStock, int reorderPoint) {
        return previousStock <= reorderPoint;
    }
}
