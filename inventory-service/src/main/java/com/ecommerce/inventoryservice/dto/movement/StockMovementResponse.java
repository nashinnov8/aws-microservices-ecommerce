package com.ecommerce.inventoryservice.dto.movement;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for stock movement history (audit trail).
 *
 * @param id Movement record UUID
 * @param inventoryId Related inventory item UUID
 * @param sku SKU for display
 * @param warehouseId Warehouse UUID (if warehouse-specific)
 * @param warehouseCode Warehouse code (if warehouse-specific)
 * @param movementType Type of movement (RECEIVE, SHIP, ADJUST, etc.)
 * @param quantity Quantity moved
 * @param previousQuantity Stock before movement
 * @param newQuantity Stock after movement
 * @param reason Reason for movement
 * @param referenceId External reference (order ID, PO number, etc.)
 * @param performedBy User or system that performed the movement
 * @param performedAt When the movement occurred
 */
public record StockMovementResponse(
        UUID id,
        UUID inventoryId,
        String sku,
        UUID warehouseId,
        String warehouseCode,
        String movementType,
        int quantity,
        int previousQuantity,
        int newQuantity,
        String reason,
        String referenceId,
        String performedBy,
        Instant performedAt
) {}
