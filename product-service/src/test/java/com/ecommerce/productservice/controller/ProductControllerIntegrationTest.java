package com.ecommerce.productservice.controller;

import com.ecommerce.productservice.domain.entity.Brand;
import com.ecommerce.productservice.domain.entity.Category;
import com.ecommerce.productservice.domain.entity.Product;
import com.ecommerce.productservice.domain.enums.ProductStatus;
import com.ecommerce.productservice.domain.repository.BrandRepository;
import com.ecommerce.productservice.domain.repository.CategoryRepository;
import com.ecommerce.productservice.domain.repository.ProductRepository;
import com.ecommerce.productservice.dto.product.ProductRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("ProductController Integration Tests")
class ProductControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private BrandRepository brandRepository;

    private Category testCategory;
    private Brand testBrand;
    private Product testProduct;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        brandRepository.deleteAll();

        testCategory = new Category();
        testCategory.setName("Electronics");
        testCategory.setDescription("Electronic devices");
        testCategory.setIsActive(true);
        testCategory = categoryRepository.save(testCategory);

        testBrand = new Brand();
        testBrand.setName("Apple");
        testBrand.setDescription("Apple Inc.");
        testBrand.setIsActive(true);
        testBrand = brandRepository.save(testBrand);

        testProduct = new Product();
        testProduct.setName("iPhone 15");
        testProduct.setDescription("Latest iPhone");
        testProduct.setBaseSku("IPHONE-15");
        testProduct.setPrice(new BigDecimal("999.99"));
        testProduct.setStatus(ProductStatus.ACTIVE);
        testProduct.setCategory(testCategory);
        testProduct.setBrand(testBrand);
        testProduct = productRepository.save(testProduct);
    }

    @Nested
    @DisplayName("POST /api/products")
    class CreateProductTests {

        @Test
        @WithMockUser
        @DisplayName("Should create product successfully")
        void createProduct_ValidRequest_Returns201() throws Exception {
            // Given
            ProductRequest request = new ProductRequest(
                    "MacBook Pro",
                    "Professional laptop",
                    "MACBOOK-PRO-16",
                    new BigDecimal("2499.99"),
                    "https://example.com/macbook.jpg",
                    testCategory.getId(),
                    testBrand.getId()
            );

            // When
            ResultActions result = mockMvc.perform(post("/api/products")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)));

            // Then
            result.andExpect(status().isCreated())
                    .andExpect(jsonPath("$.code").value("201"))
                    .andExpect(jsonPath("$.message").value("Product created successfully"))
                    .andExpect(jsonPath("$.data.name").value("MacBook Pro"))
                    .andExpect(jsonPath("$.data.baseSku").value("MACBOOK-PRO-16"))
                    .andExpect(jsonPath("$.data.price").value(2499.99))
                    .andExpect(jsonPath("$.data.categoryName").value("Electronics"))
                    .andExpect(jsonPath("$.data.brand").value("Apple"));
        }

        @Test
        @WithMockUser
        @DisplayName("Should return 400 when name is blank")
        void createProduct_BlankName_Returns400() throws Exception {
            // Given
            ProductRequest request = new ProductRequest(
                    "",
                    "Description",
                    "SKU-001",
                    new BigDecimal("100.00"),
                    null,
                    null,
                    null
            );

            // When
            ResultActions result = mockMvc.perform(post("/api/products")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)));

            // Then
            result.andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser
        @DisplayName("Should return 400 when SKU is blank")
        void createProduct_BlankSku_Returns400() throws Exception {
            // Given
            ProductRequest request = new ProductRequest(
                    "Product Name",
                    "Description",
                    "",
                    new BigDecimal("100.00"),
                    null,
                    null,
                    null
            );

            // When
            ResultActions result = mockMvc.perform(post("/api/products")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)));

            // Then
            result.andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser
        @DisplayName("Should return 404 when category not found")
        void createProduct_CategoryNotFound_Returns404() throws Exception {
            // Given
            ProductRequest request = new ProductRequest(
                    "Product",
                    "Description",
                    "SKU-001",
                    new BigDecimal("100.00"),
                    null,
                    UUID.randomUUID(),
                    null
            );

            // When
            ResultActions result = mockMvc.perform(post("/api/products")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)));

            // Then
            result.andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("404"))
                    .andExpect(jsonPath("$.message").value(containsString("Category not found")));
        }
    }

    @Nested
    @DisplayName("GET /api/products")
    class GetAllProductsTests {

        @Test
        @WithMockUser
        @DisplayName("Should get all products with pagination")
        void getAllProducts_ReturnsPagedResults() throws Exception {
            // When
            ResultActions result = mockMvc.perform(get("/api/products")
                    .param("pageNumber", "1")
                    .param("pageSize", "10"));

            // Then
            result.andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("200"))
                    .andExpect(jsonPath("$.data.content").isArray())
                    .andExpect(jsonPath("$.data.content", hasSize(1)))
                    .andExpect(jsonPath("$.data.content[0].name").value("iPhone 15"))
                    .andExpect(jsonPath("$.data.pageNumber").value(1))
                    .andExpect(jsonPath("$.data.totalElements").value(1));
        }

        @Test
        @WithMockUser
        @DisplayName("Should return empty page when no products")
        void getAllProducts_NoProducts_ReturnsEmptyPage() throws Exception {
            // Given
            productRepository.deleteAll();

            // When
            ResultActions result = mockMvc.perform(get("/api/products")
                    .param("pageNumber", "1")
                    .param("pageSize", "10"));

            // Then
            result.andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").isEmpty())
                    .andExpect(jsonPath("$.data.totalElements").value(0));
        }
    }

    @Nested
    @DisplayName("GET /api/products/{id}")
    class GetProductByIdTests {

        @Test
        @WithMockUser
        @DisplayName("Should get product by ID successfully")
        void getProductById_ExistingProduct_Returns200() throws Exception {
            // When
            ResultActions result = mockMvc.perform(get("/api/products/{id}", testProduct.getId()));

            // Then
            result.andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("200"))
                    .andExpect(jsonPath("$.data.id").value(testProduct.getId().toString()))
                    .andExpect(jsonPath("$.data.name").value("iPhone 15"))
                    .andExpect(jsonPath("$.data.baseSku").value("IPHONE-15"));
        }

        @Test
        @WithMockUser
        @DisplayName("Should return 404 when product not found")
        void getProductById_NonExistingProduct_Returns404() throws Exception {
            // When
            ResultActions result = mockMvc.perform(get("/api/products/{id}", UUID.randomUUID()));

            // Then
            result.andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("404"))
                    .andExpect(jsonPath("$.message").value(containsString("Product not found")));
        }
    }

    @Nested
    @DisplayName("PUT /api/products/{id}")
    class UpdateProductTests {

        @Test
        @WithMockUser
        @DisplayName("Should update product successfully")
        void updateProduct_ValidRequest_Returns200() throws Exception {
            // Given
            ProductRequest request = new ProductRequest(
                    "iPhone 15 Pro",
                    "Updated description",
                    "IPHONE-15-PRO",
                    new BigDecimal("1199.99"),
                    null,
                    testCategory.getId(),
                    testBrand.getId()
            );

            // When
            ResultActions result = mockMvc.perform(put("/api/products/{id}", testProduct.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)));

            // Then
            result.andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("200"))
                    .andExpect(jsonPath("$.data.name").value("iPhone 15 Pro"))
                    .andExpect(jsonPath("$.data.price").value(1199.99));
        }

        @Test
        @WithMockUser
        @DisplayName("Should return 404 when updating non-existing product")
        void updateProduct_NonExistingProduct_Returns404() throws Exception {
            // Given
            ProductRequest request = new ProductRequest(
                    "Product", "Desc", "SKU", new BigDecimal("100"), null, null, null
            );

            // When
            ResultActions result = mockMvc.perform(put("/api/products/{id}", UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)));

            // Then
            result.andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("DELETE /api/products/{id}")
    class DeleteProductTests {

        @Test
        @WithMockUser
        @DisplayName("Should delete product successfully")
        void deleteProduct_ExistingProduct_Returns200() throws Exception {
            // When
            ResultActions result = mockMvc.perform(delete("/api/products/{id}", testProduct.getId()));

            // Then
            result.andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("200"))
                    .andExpect(jsonPath("$.message").value("Product deleted successfully"));
        }

        @Test
        @WithMockUser
        @DisplayName("Should return 404 when deleting non-existing product")
        void deleteProduct_NonExistingProduct_Returns404() throws Exception {
            // When
            ResultActions result = mockMvc.perform(delete("/api/products/{id}", UUID.randomUUID()));

            // Then
            result.andExpect(status().isNotFound());
        }
    }
}
