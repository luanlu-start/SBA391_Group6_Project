package com.fptpost.modules.moderation.controller;

import com.fptpost.common.response.ApiResponse;
import com.fptpost.modules.moderation.dto.request.ModerationKeywordRequest;
import com.fptpost.modules.moderation.dto.response.ModerationKeywordResponse;
import com.fptpost.modules.moderation.service.ModerationKeywordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/moderation-keywords")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Moderation keywords", description = "Từ khóa cấm/nghi ngờ dùng cho kiểm duyệt tự động (BR-C-012)")
public class ModerationKeywordController {

    private final ModerationKeywordService moderationKeywordService;

    @GetMapping
    @Operation(summary = "Danh sách từ khóa kiểm duyệt")
    public ResponseEntity<ApiResponse<List<ModerationKeywordResponse>>> list() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(moderationKeywordService.list()));
    }

    @PostMapping
    @Operation(summary = "Thêm từ khóa")
    public ResponseEntity<ApiResponse<ModerationKeywordResponse>> create(@Valid @RequestBody ModerationKeywordRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(ApiResponse.created("Đã thêm từ khóa kiểm duyệt", moderationKeywordService.create(request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa từ khóa")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        moderationKeywordService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
