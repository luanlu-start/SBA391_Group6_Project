package com.fptpost.modules.catalog.controller;

import com.fptpost.common.response.ApiResponse;
import com.fptpost.modules.catalog.dto.request.CategoryRequest;
import com.fptpost.modules.catalog.dto.response.CategoryResponse;
import com.fptpost.modules.catalog.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/categories")
@RequiredArgsConstructor
@Tag(name = "Admin Categories", description = "UC-58 Administrator Item Category Management (API-148, API-149)")
public class AdminCategoryController {

    private final CategoryService categoryService;

    @GetMapping
    @Operation(summary = "List all item categories for management (API-148, ADMIN only)")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> listAll() {
        return ResponseEntity.ok(ApiResponse.success(categoryService.getAllCategoriesForAdmin()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get single item category details by ID (ADMIN only)")
    public ResponseEntity<ApiResponse<CategoryResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(categoryService.getCategoryById(id)));
    }

    @PostMapping
    @Operation(summary = "Create a new item category (ADMIN only)")
    public ResponseEntity<ApiResponse<CategoryResponse>> create(@Valid @RequestBody CategoryRequest request) {
        CategoryResponse response = categoryService.createCategory(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an item category (API-149, ADMIN only)")
    public ResponseEntity<ApiResponse<CategoryResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(ApiResponse.success(categoryService.updateCategory(id, request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete or deactivate category per rule A1 (ADMIN only)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> delete(@PathVariable Long id) {
        boolean hardDeleted = categoryService.deleteOrDeactivateCategory(id);
        String message = hardDeleted
                ? "Loại hàng hóa đã được xóa thành công khỏi hệ thống."
                : "Loại hàng hóa đang được sử dụng trong đơn hàng nên đã được chuyển sang trạng thái Ẩn theo quy định.";
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "hardDeleted", hardDeleted,
                "message", message
        )));
    }

    @PatchMapping("/{id}/toggle-active")
    @Operation(summary = "Toggle active / hidden state of an item category (ADMIN only)")
    public ResponseEntity<ApiResponse<CategoryResponse>> toggleActive(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(categoryService.toggleActive(id)));
    }
}
