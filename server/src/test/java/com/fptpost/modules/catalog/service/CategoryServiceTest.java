package com.fptpost.modules.catalog.service;

import com.fptpost.common.exception.AppException;
import com.fptpost.modules.catalog.dto.request.CategoryRequest;
import com.fptpost.modules.catalog.dto.response.CategoryResponse;
import com.fptpost.modules.catalog.exception.CategoryErrorCode;
import com.fptpost.modules.catalog.mapper.CategoryMapper;
import com.fptpost.modules.catalog.model.ItemCategory;
import com.fptpost.modules.catalog.repository.ItemCategoryRepository;
import com.fptpost.modules.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CategoryServiceTest {

    private ItemCategoryRepository itemCategoryRepository;
    private OrderRepository orderRepository;
    private CategoryMapper categoryMapper;
    private CategoryService categoryService;

    @BeforeEach
    void setUp() {
        itemCategoryRepository = mock(ItemCategoryRepository.class);
        orderRepository = mock(OrderRepository.class);
        categoryMapper = Mappers.getMapper(CategoryMapper.class);
        categoryService = new CategoryService(itemCategoryRepository, orderRepository, categoryMapper);
    }

    @Test
    void createCategory_Success() {
        CategoryRequest request = CategoryRequest.builder()
                .code("electronics")
                .name("Thiết bị điện tử")
                .description("Điện thoại laptop")
                .isProhibited(false)
                .requiresReview(false)
                .active(true)
                .build();

        when(itemCategoryRepository.existsByCode("ELECTRONICS")).thenReturn(false);
        when(itemCategoryRepository.save(any(ItemCategory.class))).thenAnswer(invocation -> {
            ItemCategory entity = invocation.getArgument(0);
            entity.setId(10L);
            entity.setCreatedAt(Instant.now());
            entity.setUpdatedAt(Instant.now());
            return entity;
        });

        CategoryResponse response = categoryService.createCategory(request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getCode()).isEqualTo("ELECTRONICS");
        assertThat(response.getName()).isEqualTo("Thiết bị điện tử");
        verify(itemCategoryRepository).save(any(ItemCategory.class));
    }

    @Test
    void createCategory_DuplicateCode_ThrowsException() {
        CategoryRequest request = CategoryRequest.builder()
                .code("DOCUMENTS")
                .name("Tài liệu")
                .build();

        when(itemCategoryRepository.existsByCode("DOCUMENTS")).thenReturn(true);

        assertThatThrownBy(() -> categoryService.createCategory(request))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode())
                        .isEqualTo(CategoryErrorCode.CATEGORY_CODE_ALREADY_EXISTS));

        verify(itemCategoryRepository, never()).save(any());
    }

    @Test
    void updateCategory_Success() {
        ItemCategory existing = ItemCategory.builder()
                .id(1L)
                .code("DOCS")
                .name("Tài liệu cũ")
                .active(true)
                .build();

        CategoryRequest request = CategoryRequest.builder()
                .code("DOCS")
                .name("Tài liệu mới")
                .description("Mô tả mới")
                .active(true)
                .build();

        when(itemCategoryRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(itemCategoryRepository.existsByCodeAndIdNot("DOCS", 1L)).thenReturn(false);
        when(itemCategoryRepository.save(any(ItemCategory.class))).thenAnswer(inv -> inv.getArgument(0));

        CategoryResponse response = categoryService.updateCategory(1L, request);

        assertThat(response.getName()).isEqualTo("Tài liệu mới");
        assertThat(response.getDescription()).isEqualTo("Mô tả mới");
        verify(itemCategoryRepository).save(existing);
    }

    @Test
    void deleteOrDeactivateCategory_WhenInUse_PerformsSoftDelete() {
        ItemCategory existing = ItemCategory.builder()
                .id(1L)
                .code("DOCUMENTS")
                .name("Tài liệu")
                .active(true)
                .build();

        when(itemCategoryRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(orderRepository.existsByCategoryId(1L)).thenReturn(true);

        boolean hardDeleted = categoryService.deleteOrDeactivateCategory(1L);

        assertThat(hardDeleted).isFalse();
        assertThat(existing.getActive()).isFalse();
        verify(itemCategoryRepository).save(existing);
        verify(itemCategoryRepository, never()).delete(any());
    }

    @Test
    void deleteOrDeactivateCategory_WhenNotInUse_PerformsHardDelete() {
        ItemCategory existing = ItemCategory.builder()
                .id(2L)
                .code("TEMP")
                .name("Tạm thời")
                .active(true)
                .build();

        when(itemCategoryRepository.findById(2L)).thenReturn(Optional.of(existing));
        when(orderRepository.existsByCategoryId(2L)).thenReturn(false);

        boolean hardDeleted = categoryService.deleteOrDeactivateCategory(2L);

        assertThat(hardDeleted).isTrue();
        verify(itemCategoryRepository).delete(existing);
        verify(itemCategoryRepository, never()).save(any());
    }

    @Test
    void toggleActive_Success() {
        ItemCategory existing = ItemCategory.builder()
                .id(3L)
                .code("CLOTHING")
                .name("Quần áo")
                .active(true)
                .build();

        when(itemCategoryRepository.findById(3L)).thenReturn(Optional.of(existing));
        when(itemCategoryRepository.save(any(ItemCategory.class))).thenAnswer(inv -> inv.getArgument(0));

        CategoryResponse response = categoryService.toggleActive(3L);

        assertThat(response.getActive()).isFalse();
        verify(itemCategoryRepository).save(existing);
    }
}
