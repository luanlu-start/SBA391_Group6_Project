package com.fptpost.modules.identity.controller;

import com.fptpost.common.response.ApiResponse;
import com.fptpost.common.response.PageResponse;
import com.fptpost.modules.identity.dto.request.*;
import com.fptpost.modules.identity.dto.response.*;
import com.fptpost.modules.identity.service.KycReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/mod/kyc") @RequiredArgsConstructor
@Tag(name = "KYC review", description = "UC-09 review queue and protected document access")
public class KycReviewController {
    private final KycReviewService reviews;

    @GetMapping @Operation(summary = "List KYC requests; ADMIN or MODERATOR")
    public ResponseEntity<ApiResponse<PageResponse<KycReviewResponse>>> list(
            @Valid @ModelAttribute @ParameterObject KycReviewSearchRequest request) {
        return json(reviews.list(request));
    }

    @GetMapping("/rejection-reasons") @Operation(summary = "List rejection reasons and note requirements")
    public ResponseEntity<ApiResponse<List<KycRejectionReasonResponse>>> reasons() { return json(reviews.rejectionReasons()); }

    @GetMapping("/{id}") @Operation(summary = "Read a KYC request with masked identity number")
    public ResponseEntity<ApiResponse<KycReviewResponse>> detail(@PathVariable UUID id) { return json(reviews.detail(id)); }

    @PostMapping("/{id}/approve") @Operation(summary = "Approve a pending request at the expected version")
    public ResponseEntity<ApiResponse<KycReviewResponse>> approve(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @Valid @RequestBody KycReviewVersionRequest request) {
        return json(reviews.approve(actor(jwt), session(jwt), id, request));
    }

    @PostMapping("/{id}/reject") @Operation(summary = "Reject a pending request with an applicant-visible reason")
    public ResponseEntity<ApiResponse<KycReviewResponse>> reject(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @Valid @RequestBody KycReviewRejectRequest request) {
        return json(reviews.reject(actor(jwt), session(jwt), id, request));
    }

    @PostMapping("/{id}/flag") @Operation(summary = "Keep a request pending and escalate final review to ADMIN")
    public ResponseEntity<ApiResponse<KycReviewResponse>> flag(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @Valid @RequestBody KycReviewFlagRequest request) {
        return json(reviews.flag(actor(jwt), session(jwt), id, request));
    }

    @PostMapping("/{id}/files/{fileId}/link") @Operation(summary = "Issue a five-minute image link bound to the reviewer's session")
    public ResponseEntity<ApiResponse<SignedFileLinkResponse>> link(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id, @PathVariable UUID fileId) {
        return json(reviews.fileLink(actor(jwt), session(jwt), id, fileId));
    }

    @GetMapping("/{id}/files/{fileId}") @Operation(summary = "Read an encrypted KYC image with a session-bound signature; audit access")
    public ResponseEntity<byte[]> file(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable UUID fileId,
            @RequestParam long expires, @RequestParam String signature) {
        var image = reviews.readFile(actor(jwt), session(jwt), id, fileId, expires, signature);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType(image.mediaType()))
                .header("X-Content-Type-Options", "nosniff").header("Content-Security-Policy", "default-src 'none'")
                .header("Content-Disposition", "inline; filename=kyc-image").body(image.bytes());
    }

    private <T> ResponseEntity<ApiResponse<T>> json(T data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(data));
    }
    private UUID actor(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
    private UUID session(Jwt jwt) { return UUID.fromString(jwt.getClaimAsString("sid")); }
}
