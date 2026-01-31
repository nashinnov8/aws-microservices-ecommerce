package com.ecommerce.productservice.service;

import com.ecommerce.productservice.domain.entity.Category;
import com.ecommerce.productservice.domain.repository.CategoryRepository;
import com.ecommerce.productservice.dto.category.CategoryRequest;
import com.ecommerce.productservice.dto.category.CategoryResponse;
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
@DisplayName("CategoryService Unit Tests")
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryService categoryService;

    private Category testCategory;
    private Category parentCategory;
    private UUID categoryId;
    private UUID parentId;

    @BeforeEach
    void setUp() {
        categoryId = UUID.randomUUID();
        parentId = UUID.randomUUID();

        parentCategory = new Category();
        parentCategory.setId(parentId);
        parentCategory.setName("Electronics");
        parentCategory.setDescription("All electronics");
        parentCategory.setIsActive(true);
        parentCategory.setCreatedAt(Instant.now());

        testCategory = new Category();
        testCategory.setId(categoryId);
        testCategory.setName("Smartphones");
        testCategory.setDescription("Mobile phones");
        testCategory.setImageUrl("https://example.com/smartphones.jpg");
        testCategory.setIsActive(true);
        testCategory.setParent(parentCategory);
        testCategory.setCreatedAt(Instant.now());
    }

    @Nested
    @DisplayName("Create Category Tests")
    class CreateCategoryTests {

        @Test
        @DisplayName("Should create category without parent successfully")
        void createCategory_WithoutParent_Success() {
            // Given
            CategoryRequest request = new CategoryRequest(
                    "Electronics",
                    "All electronic devices",
                    "https://example.com/electronics.jpg",
                    null
            );

            Category savedCategory = new Category();
            savedCategory.setId(UUID.randomUUID());
            savedCategory.setName("Electronics");
            savedCategory.setDescription("All electronic devices");
            savedCategory.setImageUrl("https://example.com/electronics.jpg");
            savedCategory.setIsActive(true);
            savedCategory.setCreatedAt(Instant.now());

            when(categoryRepository.save(any(Category.class))).thenReturn(savedCategory);

            // When
            CategoryResponse response = categoryService.create(request);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.name()).isEqualTo("Electronics");
            assertThat(response.description()).isEqualTo("All electronic devices");
            assertThat(response.isActive()).isTrue();
            assertThat(response.parentId()).isNull();

            verify(categoryRepository).save(any(Category.class));
            verify(categoryRepository, never()).findById(any());
        }

        @Test
        @DisplayName("Should create category with parent successfully")
        void createCategory_WithParent_Success() {
            // Given
            CategoryRequest request = new CategoryRequest(
                    "Smartphones",
                    "Mobile phones",
                    "https://example.com/phones.jpg",
                    parentId
            );

            when(categoryRepository.findById(parentId)).thenReturn(Optional.of(parentCategory));
            when(categoryRepository.save(any(Category.class))).thenReturn(testCategory);

            // When
            CategoryResponse response = categoryService.create(request);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.name()).isEqualTo("Smartphones");
            assertThat(response.parentId()).isEqualTo(parentId);

            verify(categoryRepository).findById(parentId);
            verify(categoryRepository).save(any(Category.class));
        }

        @Test
        @DisplayName("Should throw exception when parent category not found")
        void createCategory_ParentNotFound_ThrowsException() {
            // Given
            UUID nonExistentParentId = UUID.randomUUID();
            CategoryRequest request = new CategoryRequest(
                    "Smartphones",
                    "Mobile phones",
                    null,
                    nonExistentParentId
            );

            when(categoryRepository.findById(nonExistentParentId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> categoryService.create(request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Parent category not found");

            verify(categoryRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Get Category Tests")
    class GetCategoryTests {

        @Test
        @DisplayName("Should get category by ID successfully")
        void getById_ExistingCategory_Success() {
            // Given
            when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(testCategory));

            // When
            CategoryResponse response = categoryService.getById(categoryId);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(categoryId);
            assertThat(response.name()).isEqualTo("Smartphones");

            verify(categoryRepository).findById(categoryId);
        }

        @Test
        @DisplayName("Should throw exception when category not found")
        void getById_NonExistingCategory_ThrowsException() {
            // Given
            UUID nonExistentId = UUID.randomUUID();
            when(categoryRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> categoryService.getById(nonExistentId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Category not found");
        }

        @Test
        @DisplayName("Should get all categories successfully")
        void getAll_ReturnsAllCategories() {
            // Given
            List<Category> categories = List.of(testCategory, parentCategory);
            when(categoryRepository.findAll()).thenReturn(categories);

            // When
            List<CategoryResponse> responses = categoryService.getAll();

            // Then
            assertThat(responses).hasSize(2);
            verify(categoryRepository).findAll();
        }

        @Test
        @DisplayName("Should return empty list when no categories exist")
        void getAll_NoCategories_ReturnsEmptyList() {
            // Given
            when(categoryRepository.findAll()).thenReturn(List.of());

            // When
            List<CategoryResponse> responses = categoryService.getAll();

            // Then
            assertThat(responses).isEmpty();
        }
    }

    @Nested
    @DisplayName("Update Category Tests")
    class UpdateCategoryTests {

        @Test
        @DisplayName("Should update category successfully")
        void updateCategory_Success() {
            // Given
            CategoryRequest request = new CategoryRequest(
                    "Updated Smartphones",
                    "Updated description",
                    "https://example.com/updated.jpg",
                    null
            );

            Category updatedCategory = new Category();
            updatedCategory.setId(categoryId);
            updatedCategory.setName("Updated Smartphones");
            updatedCategory.setDescription("Updated description");
            updatedCategory.setImageUrl("https://example.com/updated.jpg");
            updatedCategory.setIsActive(true);
            updatedCategory.setCreatedAt(Instant.now());

            when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(testCategory));
            when(categoryRepository.save(any(Category.class))).thenReturn(updatedCategory);

            // When
            CategoryResponse response = categoryService.update(categoryId, request);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.name()).isEqualTo("Updated Smartphones");

            verify(categoryRepository).findById(categoryId);
            verify(categoryRepository).save(any(Category.class));
        }

        @Test
        @DisplayName("Should update category with new parent")
        void updateCategory_WithNewParent_Success() {
            // Given
            UUID newParentId = UUID.randomUUID();
            Category newParent = new Category();
            newParent.setId(newParentId);
            newParent.setName("New Parent");

            CategoryRequest request = new CategoryRequest(
                    "Smartphones",
                    "Description",
                    null,
                    newParentId
            );

            when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(testCategory));
            when(categoryRepository.findById(newParentId)).thenReturn(Optional.of(newParent));
            when(categoryRepository.save(any(Category.class))).thenAnswer(i -> i.getArgument(0));

            // When
            categoryService.update(categoryId, request);

            // Then
            verify(categoryRepository).save(argThat(cat -> cat.getParent().getId().equals(newParentId)));
        }

        @Test
        @DisplayName("Should throw exception when updating non-existing category")
        void updateCategory_NotFound_ThrowsException() {
            // Given
            UUID nonExistentId = UUID.randomUUID();
            CategoryRequest request = new CategoryRequest("Name", "Desc", null, null);

            when(categoryRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> categoryService.update(nonExistentId, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Category not found");

            verify(categoryRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Delete Category Tests")
    class DeleteCategoryTests {

        @Test
        @DisplayName("Should delete category successfully")
        void deleteCategory_Success() {
            // Given
            when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(testCategory));
            doNothing().when(categoryRepository).delete(testCategory);

            // When
            categoryService.delete(categoryId);

            // Then
            verify(categoryRepository).findById(categoryId);
            verify(categoryRepository).delete(testCategory);
        }

        @Test
        @DisplayName("Should throw exception when deleting non-existing category")
        void deleteCategory_NotFound_ThrowsException() {
            // Given
            UUID nonExistentId = UUID.randomUUID();
            when(categoryRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> categoryService.delete(nonExistentId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Category not found");

            verify(categoryRepository, never()).delete(any());
        }
    }
}
