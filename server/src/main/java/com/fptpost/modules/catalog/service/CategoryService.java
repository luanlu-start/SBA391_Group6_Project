package com.fptpost.modules.catalog.service;

import com.fptpost.common.exception.AppException;
import com.fptpost.modules.catalog.dto.request.CategoryRequest;
import com.fptpost.modules.catalog.dto.response.CategoryResponse;
import com.fptpost.modules.catalog.exception.CategoryErrorCode;
import com.fptpost.modules.catalog.mapper.CategoryMapper;
import com.fptpost.modules.catalog.model.ItemCategory;
import com.fptpost.modules.catalog.repository.ItemCategoryRepository;
import com.fptpost.modules.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final ItemCategoryRepository itemCategoryRepository;
    private final OrderRepository orderRepository;
    private final CategoryMapper categoryMapper;

    /**
     * Lấy toàn bộ danh mục cho Admin (API-148 / UC-58).
     */
    public List<CategoryResponse> getAllCategoriesForAdmin() {
        return categoryMapper.toResponses(itemCategoryRepository.findAllByOrderByCreatedAtDesc());
    }

    /**
     * Lấy danh sách danh mục đang hoạt động (active = true) cho người dùng/khách hàng tạo đơn.
     */
    public List<CategoryResponse> getActiveCategories() {
        return categoryMapper.toResponses(itemCategoryRepository.findByActiveTrueOrderByNameAsc());
    }

    /**
     * Lấy chi tiết một loại hàng hóa theo ID.
     */
    public CategoryResponse getCategoryById(Long id) {
        ItemCategory category = itemCategoryRepository.findById(id)
                .orElseThrow(() -> new AppException(CategoryErrorCode.CATEGORY_NOT_FOUND));
        return categoryMapper.toResponse(category);
    }

    /**
     * Tạo mới một loại hàng hóa.
     */
    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        String code = request.getCode().trim().toUpperCase();
        if (itemCategoryRepository.existsByCode(code)) {
            throw new AppException(CategoryErrorCode.CATEGORY_CODE_ALREADY_EXISTS);
        }

        ItemCategory category = categoryMapper.toEntity(request);
        category.setCode(code);
        if (category.getActive() == null) {
            category.setActive(true);
        }

        ItemCategory saved = itemCategoryRepository.save(category);
        log.info("Created new item category: id={}, code={}", saved.getId(), saved.getCode());
        return categoryMapper.toResponse(saved);
    }

    /**
     * Cập nhật thông tin loại hàng hóa (API-149).
     */
    @Transactional
    public CategoryResponse updateCategory(Long id, CategoryRequest request) {
        ItemCategory category = itemCategoryRepository.findById(id)
                .orElseThrow(() -> new AppException(CategoryErrorCode.CATEGORY_NOT_FOUND));

        String code = request.getCode().trim().toUpperCase();
        if (itemCategoryRepository.existsByCodeAndIdNot(code, id)) {
            throw new AppException(CategoryErrorCode.CATEGORY_CODE_ALREADY_EXISTS);
        }

        categoryMapper.updateEntity(request, category);
        category.setCode(code);

        ItemCategory saved = itemCategoryRepository.save(category);
        log.info("Updated item category: id={}, code={}", saved.getId(), saved.getCode());
        return categoryMapper.toResponse(saved);
    }

    /**
     * Xóa loại hàng hóa theo quy tắc BR-F-113 & Ngoại lệ A1:
     * - Nếu đã có đơn hàng sử dụng: Tự động chuyển active = false (Soft-delete / Ẩn).
     * - Nếu chưa có đơn hàng sử dụng: Xóa cứng khỏi CSDL.
     * @return true nếu đã xóa cứng, false nếu chỉ chuyển sang ẩn (soft-delete).
     */
    @Transactional
    public boolean deleteOrDeactivateCategory(Long id) {
        ItemCategory category = itemCategoryRepository.findById(id)
                .orElseThrow(() -> new AppException(CategoryErrorCode.CATEGORY_NOT_FOUND));

        boolean isInUse = orderRepository.existsByCategoryId(id);
        if (isInUse) {
            log.info("Category id={} is in use by orders. Soft-deactivating (active = false) per rule A1.", id);
            category.setActive(false);
            itemCategoryRepository.save(category);
            return false;
        } else {
            log.info("Category id={} is not in use. Performing hard delete.", id);
            itemCategoryRepository.delete(category);
            return true;
        }
    }

    /**
     * Bật / Tắt trạng thái hoạt động (active) của loại hàng hóa.
     */
    @Transactional
    public CategoryResponse toggleActive(Long id) {
        ItemCategory category = itemCategoryRepository.findById(id)
                .orElseThrow(() -> new AppException(CategoryErrorCode.CATEGORY_NOT_FOUND));

        boolean newActive = !Boolean.TRUE.equals(category.getActive());
        category.setActive(newActive);
        ItemCategory saved = itemCategoryRepository.save(category);
        log.info("Toggled active status for category id={}: new status={}", id, newActive);
        return categoryMapper.toResponse(saved);
    }
}
