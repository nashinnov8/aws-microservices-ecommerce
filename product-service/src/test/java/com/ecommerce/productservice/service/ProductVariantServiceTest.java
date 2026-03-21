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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductVariantService Unit Tests")
class ProductVariantServiceTest {

    @Mock
    private ProductVariantRepository productVariantRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductEventProducer productEventProducer;

    @InjectMocks
    private ProductVariantService productVariantService;

    private Product testProduct;
    private ProductVariant testVariant;
    private UUID productId;
    private UUID variantId;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID();
        variantId = UUID.randomUUID();

        testProduct = new Product();
        testProduct.setId(productId);
        testProduct.setName("iPhone 15");
        testProduct.setBaseSku("IPHONE-15");
        testProduct.setPrice(new BigDecimal("999.99"));
        testProduct.setStatus(ProductStatus.ACTIVE);
        testProduct.setVariants(new ArrayList<>());
        testProduct.setCreatedAt(Instant.now());

        testVariant = new ProductVariant();
        testVariant.setId(variantId);
        testVariant.setVariantSku("IPHONE-15-BLK-128");
        testVariant.setProduct(testProduct);
        testVariant.setSize("128GB");
        testVariant.setColor("Black");
        testVariant.setMaterial("Aluminum");
        testVariant.setPriceAdjustment(BigDecimal.ZERO);
        testVariant.setStatus(ProductStatus.ACTIVE);
        testVariant.setCreatedAt(Instant.now());
    }

    @Nested
    @DisplayName("Create Variant Tests")
    class CreateVariantTests {

        @Test
        @DisplayName("Should create variant successfully")
        void createVariant_Success() {
            // Given
            ProductVariantRequest request = new ProductVariantRequest(
                    "IPHONE-15-WHT-256",
                    "256GB",
                    "White",
                    "Titanium",
                    new BigDecimal("100.00"),
                    "https://example.com/white.jpg"
            );

            ProductVariant savedVariant = new ProductVariant();
            savedVariant.setId(UUID.randomUUID());
            savedVariant.setVariantSku("IPHONE-15-WHT-256");
            savedVariant.setProduct(testProduct);
            savedVariant.setSize("256GB");
            savedVariant.setColor("White");
            savedVariant.setMaterial("Titanium");
            savedVariant.setPriceAdjustment(new BigDecimal("100.00"));
            savedVariant.setStatus(ProductStatus.ACTIVE);
            savedVariant.setCreatedAt(Instant.now());

            when(productRepository.findById(productId)).thenReturn(Optional.of(testProduct));
            when(productVariantRepository.save(any(ProductVariant.class))).thenReturn(savedVariant);

            // When
            ProductVariantResponse response = productVariantService.create(productId, request);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.variantSku()).isEqualTo("IPHONE-15-WHT-256");
            assertThat(response.size()).isEqualTo("256GB");
            assertThat(response.color()).isEqualTo("White");
            assertThat(response.priceAdjustment()).isEqualByComparingTo(new BigDecimal("100.00"));

            verify(productRepository).findById(productId);
            verify(productVariantRepository).save(any(ProductVariant.class));
        }

        @Test
        @DisplayName("Should throw exception when product not found")
        void createVariant_ProductNotFound_ThrowsException() {
            // Given
            UUID nonExistentProductId = UUID.randomUUID();
            ProductVariantRequest request = new ProductVariantRequest(
                    "SKU", "Size", "Color", "Material", BigDecimal.ZERO, null
            );

            when(productRepository.findById(nonExistentProductId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> productVariantService.create(nonExistentProductId, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Product not found");

            verify(productVariantRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should create variant with minimal data")
        void createVariant_MinimalData_Success() {
            // Given
            ProductVariantRequest request = new ProductVariantRequest(
                    "SIMPLE-SKU",
                    null,
                    null,
                    null,
                    null,
                    null
            );

            ProductVariant savedVariant = new ProductVariant();
            savedVariant.setId(UUID.randomUUID());
            savedVariant.setVariantSku("SIMPLE-SKU");
            savedVariant.setProduct(testProduct);
            savedVariant.setStatus(ProductStatus.ACTIVE);
            savedVariant.setCreatedAt(Instant.now());

            when(productRepository.findById(productId)).thenReturn(Optional.of(testProduct));
            when(productVariantRepository.save(any(ProductVariant.class))).thenReturn(savedVariant);

            // When
            ProductVariantResponse response = productVariantService.create(productId, request);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.variantSku()).isEqualTo("SIMPLE-SKU");
            assertThat(response.size()).isNull();
            assertThat(response.color()).isNull();
        }
    }

    @Nested
    @DisplayName("Get Variant Tests")
    class GetVariantTests {

        @Test
        @DisplayName("Should get variant by ID successfully")
        void getById_ExistingVariant_Success() {
            // Given
            when(productVariantRepository.findById(variantId)).thenReturn(Optional.of(testVariant));

            // When
            ProductVariantResponse response = productVariantService.getById(variantId);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(variantId);
            assertThat(response.variantSku()).isEqualTo("IPHONE-15-BLK-128");

            verify(productVariantRepository).findById(variantId);
        }

        @Test
        @DisplayName("Should throw exception when variant not found")
        void getById_NonExistingVariant_ThrowsException() {
            // Given
            UUID nonExistentId = UUID.randomUUID();
            when(productVariantRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> productVariantService.getById(nonExistentId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Product variant not found");
        }

        @Test
        @DisplayName("Should get all variants by product ID")
        void getAllByProductId_Success() {
            // Given
            testProduct.getVariants().add(testVariant);
            when(productRepository.findById(productId)).thenReturn(Optional.of(testProduct));

            // When
            List<ProductVariantResponse> responses = productVariantService.getAllByProductId(productId);

            // Then
            assertThat(responses).hasSize(1);
            assertThat(responses.get(0).variantSku()).isEqualTo("IPHONE-15-BLK-128");

            verify(productRepository).findById(productId);
        }

        @Test
        @DisplayName("Should return empty list when product has no variants")
        void getAllByProductId_NoVariants_ReturnsEmptyList() {
            // Given
            when(productRepository.findById(productId)).thenReturn(Optional.of(testProduct));

            // When
            List<ProductVariantResponse> responses = productVariantService.getAllByProductId(productId);

            // Then
            assertThat(responses).isEmpty();
        }

        @Test
        @DisplayName("Should throw exception when product not found for variants")
        void getAllByProductId_ProductNotFound_ThrowsException() {
            // Given
            UUID nonExistentProductId = UUID.randomUUID();
            when(productRepository.findById(nonExistentProductId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> productVariantService.getAllByProductId(nonExistentProductId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Product not found");
        }
    }

    @Nested
    @DisplayName("Update Variant Tests")
    class UpdateVariantTests {

        @Test
        @DisplayName("Should update variant successfully")
        void updateVariant_Success() {
            // Given
            ProductVariantRequest request = new ProductVariantRequest(
                    "IPHONE-15-BLK-256",
                    "256GB",
                    "Space Black",
                    "Titanium",
                    new BigDecimal("200.00"),
                    "https://example.com/updated.jpg"
            );

            ProductVariant updatedVariant = new ProductVariant();
            updatedVariant.setId(variantId);
            updatedVariant.setVariantSku("IPHONE-15-BLK-256");
            updatedVariant.setProduct(testProduct);
            updatedVariant.setSize("256GB");
            updatedVariant.setColor("Space Black");
            updatedVariant.setMaterial("Titanium");
            updatedVariant.setPriceAdjustment(new BigDecimal("200.00"));
            updatedVariant.setStatus(ProductStatus.ACTIVE);
            updatedVariant.setCreatedAt(Instant.now());

            when(productVariantRepository.findById(variantId)).thenReturn(Optional.of(testVariant));
            when(productVariantRepository.save(any(ProductVariant.class))).thenReturn(updatedVariant);

            // When
            ProductVariantResponse response = productVariantService.update(variantId, request);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.variantSku()).isEqualTo("IPHONE-15-BLK-256");
            assertThat(response.size()).isEqualTo("256GB");
            assertThat(response.color()).isEqualTo("Space Black");

            verify(productVariantRepository).findById(variantId);
            verify(productVariantRepository).save(any(ProductVariant.class));
        }

        @Test
        @DisplayName("Should throw exception when updating non-existing variant")
        void updateVariant_NotFound_ThrowsException() {
            // Given
            UUID nonExistentId = UUID.randomUUID();
            ProductVariantRequest request = new ProductVariantRequest(
                    "SKU", null, null, null, null, null
            );

            when(productVariantRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> productVariantService.update(nonExistentId, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Product variant not found");

            verify(productVariantRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Delete Variant Tests")
    class DeleteVariantTests {

        @Test
        @DisplayName("Should delete variant successfully")
        void deleteVariant_Success() {
            // Given
            when(productVariantRepository.findById(variantId)).thenReturn(Optional.of(testVariant));
            doNothing().when(productVariantRepository).delete(testVariant);

            // When
            productVariantService.delete(variantId);

            // Then
            verify(productVariantRepository).findById(variantId);
            verify(productVariantRepository).delete(testVariant);
        }

        @Test
        @DisplayName("Should throw exception when deleting non-existing variant")
        void deleteVariant_NotFound_ThrowsException() {
            // Given
            UUID nonExistentId = UUID.randomUUID();
            when(productVariantRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> productVariantService.delete(nonExistentId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Product variant not found");

            verify(productVariantRepository, never()).delete(any());
        }
    }
}
