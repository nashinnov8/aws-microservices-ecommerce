package com.ecommerce.productservice.service;

import com.ecommerce.productservice.domain.entity.Brand;
import com.ecommerce.productservice.domain.repository.BrandRepository;
import com.ecommerce.productservice.dto.brand.BrandRequest;
import com.ecommerce.productservice.dto.brand.BrandResponse;
import com.ecommerce.productservice.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BrandService Unit Tests")
class BrandServiceTest {

    @Mock
    private BrandRepository brandRepository;

    @InjectMocks
    private BrandService brandService;

    private Brand testBrand;
    private UUID brandId;

    @BeforeEach
    void setUp() {
        brandId = UUID.randomUUID();

        testBrand = new Brand();
        testBrand.setId(brandId);
        testBrand.setName("Apple");
        testBrand.setDescription("Apple Inc. - Technology company");
        testBrand.setLogoUrl("https://example.com/apple-logo.png");
        testBrand.setWebsiteUrl("https://apple.com");
        testBrand.setIsActive(true);
        testBrand.setCreatedAt(Instant.now());
    }

    @Nested
    @DisplayName("Create Brand Tests")
    class CreateBrandTests {

        @Test
        @DisplayName("Should create brand successfully")
        void createBrand_Success() {
            // Given
            BrandRequest request = new BrandRequest(
                    "Apple",
                    "Apple Inc. - Technology company",
                    "https://example.com/apple-logo.png",
                    "https://apple.com"
            );

            when(brandRepository.save(any(Brand.class))).thenReturn(testBrand);

            // When
            BrandResponse response = brandService.create(request);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.name()).isEqualTo("Apple");
            assertThat(response.description()).isEqualTo("Apple Inc. - Technology company");
            assertThat(response.logoUrl()).isEqualTo("https://example.com/apple-logo.png");
            assertThat(response.websiteUrl()).isEqualTo("https://apple.com");
            assertThat(response.isActive()).isTrue();

            verify(brandRepository).save(any(Brand.class));
        }

        @Test
        @DisplayName("Should create brand with minimal data")
        void createBrand_MinimalData_Success() {
            // Given
            BrandRequest request = new BrandRequest(
                    "Simple Brand",
                    null,
                    null,
                    null
            );

            Brand savedBrand = new Brand();
            savedBrand.setId(UUID.randomUUID());
            savedBrand.setName("Simple Brand");
            savedBrand.setIsActive(true);
            savedBrand.setCreatedAt(Instant.now());

            when(brandRepository.save(any(Brand.class))).thenReturn(savedBrand);

            // When
            BrandResponse response = brandService.create(request);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.name()).isEqualTo("Simple Brand");
            assertThat(response.description()).isNull();
            assertThat(response.logoUrl()).isNull();
            assertThat(response.websiteUrl()).isNull();
        }
    }

    @Nested
    @DisplayName("Get Brand Tests")
    class GetBrandTests {

        @Test
        @DisplayName("Should get brand by ID successfully")
        void getById_ExistingBrand_Success() {
            // Given
            when(brandRepository.findById(brandId)).thenReturn(Optional.of(testBrand));

            // When
            BrandResponse response = brandService.getById(brandId);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(brandId);
            assertThat(response.name()).isEqualTo("Apple");

            verify(brandRepository).findById(brandId);
        }

        @Test
        @DisplayName("Should throw exception when brand not found")
        void getById_NonExistingBrand_ThrowsException() {
            // Given
            UUID nonExistentId = UUID.randomUUID();
            when(brandRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> brandService.getById(nonExistentId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Brand not found");
        }

        @Test
        @DisplayName("Should get all brands successfully")
        void getAll_ReturnsAllBrands() {
            // Given
            Brand anotherBrand = new Brand();
            anotherBrand.setId(UUID.randomUUID());
            anotherBrand.setName("Samsung");
            anotherBrand.setIsActive(true);
            anotherBrand.setCreatedAt(Instant.now());

            List<Brand> brands = List.of(testBrand, anotherBrand);
            when(brandRepository.findAll()).thenReturn(brands);

            // When
            List<BrandResponse> responses = brandService.getAll();

            // Then
            assertThat(responses).hasSize(2);
            assertThat(responses).extracting(BrandResponse::name)
                    .containsExactlyInAnyOrder("Apple", "Samsung");

            verify(brandRepository).findAll();
        }

        @Test
        @DisplayName("Should return empty list when no brands exist")
        void getAll_NoBrands_ReturnsEmptyList() {
            // Given
            when(brandRepository.findAll()).thenReturn(List.of());

            // When
            List<BrandResponse> responses = brandService.getAll();

            // Then
            assertThat(responses).isEmpty();
        }
    }

    @Nested
    @DisplayName("Update Brand Tests")
    class UpdateBrandTests {

        @Test
        @DisplayName("Should update brand successfully")
        void updateBrand_Success() {
            // Given
            BrandRequest request = new BrandRequest(
                    "Apple Inc.",
                    "Updated description",
                    "https://example.com/new-logo.png",
                    "https://www.apple.com"
            );

            Brand updatedBrand = new Brand();
            updatedBrand.setId(brandId);
            updatedBrand.setName("Apple Inc.");
            updatedBrand.setDescription("Updated description");
            updatedBrand.setLogoUrl("https://example.com/new-logo.png");
            updatedBrand.setWebsiteUrl("https://www.apple.com");
            updatedBrand.setIsActive(true);
            updatedBrand.setCreatedAt(Instant.now());

            when(brandRepository.findById(brandId)).thenReturn(Optional.of(testBrand));
            when(brandRepository.save(any(Brand.class))).thenReturn(updatedBrand);

            // When
            BrandResponse response = brandService.update(brandId, request);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.name()).isEqualTo("Apple Inc.");
            assertThat(response.description()).isEqualTo("Updated description");

            verify(brandRepository).findById(brandId);
            verify(brandRepository).save(any(Brand.class));
        }

        @Test
        @DisplayName("Should throw exception when updating non-existing brand")
        void updateBrand_NotFound_ThrowsException() {
            // Given
            UUID nonExistentId = UUID.randomUUID();
            BrandRequest request = new BrandRequest("Name", "Desc", null, null);

            when(brandRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> brandService.update(nonExistentId, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Brand not found");

            verify(brandRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should update brand with null optional fields")
        void updateBrand_NullOptionalFields_Success() {
            // Given
            BrandRequest request = new BrandRequest(
                    "Apple",
                    null,
                    null,
                    null
            );

            when(brandRepository.findById(brandId)).thenReturn(Optional.of(testBrand));
            when(brandRepository.save(any(Brand.class))).thenAnswer(i -> i.getArgument(0));

            // When
            brandService.update(brandId, request);

            // Then
            verify(brandRepository).save(argThat(brand ->
                brand.getDescription() == null &&
                brand.getLogoUrl() == null &&
                brand.getWebsiteUrl() == null
            ));
        }
    }

    @Nested
    @DisplayName("Delete Brand Tests")
    class DeleteBrandTests {

        @Test
        @DisplayName("Should delete brand successfully")
        void deleteBrand_Success() {
            // Given
            when(brandRepository.findById(brandId)).thenReturn(Optional.of(testBrand));
            doNothing().when(brandRepository).delete(testBrand);

            // When
            brandService.delete(brandId);

            // Then
            verify(brandRepository).findById(brandId);
            verify(brandRepository).delete(testBrand);
        }

        @Test
        @DisplayName("Should throw exception when deleting non-existing brand")
        void deleteBrand_NotFound_ThrowsException() {
            // Given
            UUID nonExistentId = UUID.randomUUID();
            when(brandRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> brandService.delete(nonExistentId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Brand not found");

            verify(brandRepository, never()).delete(any());
        }
    }
}
