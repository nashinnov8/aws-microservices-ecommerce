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
import com.ecommerce.productservice.kafka.ProductEventProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import response.PageResponse;

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
@DisplayName("ProductService Unit Tests")
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private BrandRepository brandRepository;

    @Mock
    private ProductEventProducer productEventProducer;

    @InjectMocks
    private ProductService productService;

    private Product testProduct;
    private Category testCategory;
    private Brand testBrand;
    private UUID productId;
    private UUID categoryId;
    private UUID brandId;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID();
        categoryId = UUID.randomUUID();
        brandId = UUID.randomUUID();

        testCategory = new Category();
        testCategory.setId(categoryId);
        testCategory.setName("Electronics");
        testCategory.setDescription("Electronic devices");

        testBrand = new Brand();
        testBrand.setId(brandId);
        testBrand.setName("Apple");
        testBrand.setDescription("Apple Inc.");

        testProduct = new Product();
        testProduct.setId(productId);
        testProduct.setName("iPhone 15");
        testProduct.setDescription("Latest iPhone");
        testProduct.setBaseSku("IPHONE-15");
        testProduct.setPrice(new BigDecimal("999.99"));
        testProduct.setImageUrl("https://example.com/iphone.jpg");
        testProduct.setStatus(ProductStatus.ACTIVE);
        testProduct.setCategory(testCategory);
        testProduct.setBrand(testBrand);
        testProduct.setVariants(new ArrayList<>());
        testProduct.setCreatedAt(Instant.now());
    }

    @Nested
    @DisplayName("Create Product Tests")
    class CreateProductTests {

        @Test
        @DisplayName("Should create product successfully with category and brand")
        void createProduct_WithCategoryAndBrand_Success() {
            // Given
            ProductRequest request = new ProductRequest(
                    "iPhone 15",
                    "Latest iPhone",
                    "IPHONE-15",
                    new BigDecimal("999.99"),
                    "https://example.com/iphone.jpg",
                    categoryId,
                    brandId
            );

            when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(testCategory));
            when(brandRepository.findById(brandId)).thenReturn(Optional.of(testBrand));
            when(productRepository.save(any(Product.class))).thenReturn(testProduct);

            // When
            ProductResponse response = productService.create(request);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.name()).isEqualTo("iPhone 15");
            assertThat(response.baseSku()).isEqualTo("IPHONE-15");
            assertThat(response.price()).isEqualByComparingTo(new BigDecimal("999.99"));
            assertThat(response.categoryName()).isEqualTo("Electronics");
            assertThat(response.brand()).isEqualTo("Apple");

            verify(categoryRepository).findById(categoryId);
            verify(brandRepository).findById(brandId);
            verify(productRepository).save(any(Product.class));
        }

        @Test
        @DisplayName("Should create product without category and brand")
        void createProduct_WithoutCategoryAndBrand_Success() {
            // Given
            ProductRequest request = new ProductRequest(
                    "Generic Product",
                    "A product",
                    "GEN-001",
                    new BigDecimal("49.99"),
                    null,
                    null,
                    null
            );

            Product savedProduct = new Product();
            savedProduct.setId(UUID.randomUUID());
            savedProduct.setName("Generic Product");
            savedProduct.setBaseSku("GEN-001");
            savedProduct.setPrice(new BigDecimal("49.99"));
            savedProduct.setStatus(ProductStatus.ACTIVE);
            savedProduct.setVariants(new ArrayList<>());

            when(productRepository.save(any(Product.class))).thenReturn(savedProduct);

            // When
            ProductResponse response = productService.create(request);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.name()).isEqualTo("Generic Product");
            assertThat(response.categoryName()).isNull();
            assertThat(response.brand()).isNull();

            verify(categoryRepository, never()).findById(any());
            verify(brandRepository, never()).findById(any());
        }

        @Test
        @DisplayName("Should throw exception when category not found")
        void createProduct_CategoryNotFound_ThrowsException() {
            // Given
            UUID nonExistentCategoryId = UUID.randomUUID();
            ProductRequest request = new ProductRequest(
                    "Product",
                    "Description",
                    "SKU-001",
                    new BigDecimal("100.00"),
                    null,
                    nonExistentCategoryId,
                    null
            );

            when(categoryRepository.findById(nonExistentCategoryId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> productService.create(request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Category not found");

            verify(productRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw exception when brand not found")
        void createProduct_BrandNotFound_ThrowsException() {
            // Given
            UUID nonExistentBrandId = UUID.randomUUID();
            ProductRequest request = new ProductRequest(
                    "Product",
                    "Description",
                    "SKU-001",
                    new BigDecimal("100.00"),
                    null,
                    null,
                    nonExistentBrandId
            );

            when(brandRepository.findById(nonExistentBrandId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> productService.create(request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Brand not found");

            verify(productRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Get Product Tests")
    class GetProductTests {

        @Test
        @DisplayName("Should get product by ID successfully")
        void getById_ExistingProduct_Success() {
            // Given
            when(productRepository.findById(productId)).thenReturn(Optional.of(testProduct));

            // When
            ProductResponse response = productService.getById(productId);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(productId);
            assertThat(response.name()).isEqualTo("iPhone 15");

            verify(productRepository).findById(productId);
        }

        @Test
        @DisplayName("Should throw exception when product not found")
        void getById_NonExistingProduct_ThrowsException() {
            // Given
            UUID nonExistentId = UUID.randomUUID();
            when(productRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> productService.getById(nonExistentId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Product not found");
        }

        @Test
        @DisplayName("Should get all products with pagination")
        void getAllProducts_WithPagination_Success() {
            // Given
            GetProductRequest request = new GetProductRequest(1, 10);
            List<Product> products = List.of(testProduct);
            Page<Product> productPage = new PageImpl<>(products, PageRequest.of(0, 10), 1);

            when(productRepository.findAll(any(Pageable.class))).thenReturn(productPage);

            // When
            PageResponse<ProductResponse> response = productService.getAllProducts(request);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getContent()).hasSize(1);
            assertThat(response.getPageNumber()).isEqualTo(1);
            assertThat(response.getPageSize()).isEqualTo(10);
            assertThat(response.getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should return empty page when no products exist")
        void getAllProducts_NoProducts_ReturnsEmptyPage() {
            // Given
            GetProductRequest request = new GetProductRequest(1, 10);
            Page<Product> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);

            when(productRepository.findAll(any(Pageable.class))).thenReturn(emptyPage);

            // When
            PageResponse<ProductResponse> response = productService.getAllProducts(request);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getContent()).isEmpty();
            assertThat(response.getTotalElements()).isZero();
        }
    }

    @Nested
    @DisplayName("Update Product Tests")
    class UpdateProductTests {

        @Test
        @DisplayName("Should update product successfully")
        void updateProduct_ExistingProduct_Success() {
            // Given
            ProductRequest updateRequest = new ProductRequest(
                    "iPhone 15 Pro",
                    "Updated description",
                    "IPHONE-15-PRO",
                    new BigDecimal("1199.99"),
                    "https://example.com/iphone-pro.jpg",
                    categoryId,
                    null
            );

            Product updatedProduct = new Product();
            updatedProduct.setId(productId);
            updatedProduct.setName("iPhone 15 Pro");
            updatedProduct.setDescription("Updated description");
            updatedProduct.setBaseSku("IPHONE-15-PRO");
            updatedProduct.setPrice(new BigDecimal("1199.99"));
            updatedProduct.setStatus(ProductStatus.ACTIVE);
            updatedProduct.setCategory(testCategory);
            updatedProduct.setVariants(new ArrayList<>());

            when(productRepository.findById(productId)).thenReturn(Optional.of(testProduct));
            when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(testCategory));
            when(productRepository.save(any(Product.class))).thenReturn(updatedProduct);

            // When
            ProductResponse response = productService.update(productId, updateRequest);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.name()).isEqualTo("iPhone 15 Pro");
            assertThat(response.price()).isEqualByComparingTo(new BigDecimal("1199.99"));

            verify(productRepository).findById(productId);
            verify(productRepository).save(any(Product.class));
        }

        @Test
        @DisplayName("Should throw exception when updating non-existing product")
        void updateProduct_NonExistingProduct_ThrowsException() {
            // Given
            UUID nonExistentId = UUID.randomUUID();
            ProductRequest request = new ProductRequest(
                    "Product", "Desc", "SKU", new BigDecimal("100"), null, null, null
            );

            when(productRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> productService.update(nonExistentId, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Product not found");

            verify(productRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should remove category when categoryId is null")
        void updateProduct_RemoveCategory_Success() {
            // Given
            ProductRequest request = new ProductRequest(
                    "Product", "Desc", "SKU", new BigDecimal("100"), null, null, null
            );

            when(productRepository.findById(productId)).thenReturn(Optional.of(testProduct));
            when(productRepository.save(any(Product.class))).thenAnswer(i -> i.getArgument(0));

            // When
            productService.update(productId, request);

            // Then
            verify(productRepository).save(argThat(product -> product.getCategory() == null));
        }
    }

    @Nested
    @DisplayName("Delete Product Tests")
    class DeleteProductTests {

        @Test
        @DisplayName("Should delete product successfully")
        void deleteProduct_ExistingProduct_Success() {
            // Given
            when(productRepository.findById(productId)).thenReturn(Optional.of(testProduct));
            doNothing().when(productRepository).delete(testProduct);

            // When
            productService.delete(productId);

            // Then
            verify(productRepository).findById(productId);
            verify(productRepository).delete(testProduct);
        }

        @Test
        @DisplayName("Should throw exception when deleting non-existing product")
        void deleteProduct_NonExistingProduct_ThrowsException() {
            // Given
            UUID nonExistentId = UUID.randomUUID();
            when(productRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> productService.delete(nonExistentId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Product not found");

            verify(productRepository, never()).delete(any());
        }
    }
}
