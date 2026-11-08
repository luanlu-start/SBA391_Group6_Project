package com.fptpost.modules.moderation.service;

import com.fptpost.common.audit.AuditOutboxService;
import com.fptpost.common.exception.AppException;
import com.fptpost.modules.moderation.dto.request.ModerationKeywordRequest;
import com.fptpost.modules.moderation.dto.response.ModerationKeywordResponse;
import com.fptpost.modules.moderation.exception.ModerationErrorCode;
import com.fptpost.modules.moderation.model.ModerationKeyword;
import com.fptpost.modules.moderation.repository.ModerationKeywordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Map;

/** Danh mục từ khóa cấm/nghi ngờ do Admin quản trị (BR-C-012). */
@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ModerationKeywordService {

    private final ModerationKeywordRepository keywords;
    private final AuditOutboxService audit;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<ModerationKeywordResponse> list() {
        return keywords.findAllByOrderByLevelAscTermAsc().stream().map(ModerationKeywordService::response).toList();
    }

    @Transactional
    public ModerationKeywordResponse create(ModerationKeywordRequest request) {
        String term = OrderContentScanner.normalize(request.getTerm());
        if (term.length() < 2 || term.length() > 100 || !term.matches("[\\p{L}\\p{N} .'-]+")) {
            throw new AppException(ModerationErrorCode.MODERATION_KEYWORD_INVALID);
        }
        if (keywords.existsByTerm(term)) throw new AppException(ModerationErrorCode.MODERATION_KEYWORD_DUPLICATED);
        ModerationKeyword saved = keywords.saveAndFlush(ModerationKeyword.builder()
                .term(term).level(request.getLevel()).createdAt(clock.instant()).build());
        audit.enqueue("MODERATION_KEYWORD_CREATED", "MODERATION_KEYWORD", saved.getId().toString(), Map.of(),
                Map.of("term", term, "level", saved.getLevel().name()));
        return response(saved);
    }

    @Transactional
    public void delete(Long id) {
        ModerationKeyword keyword = keywords.findById(id)
                .orElseThrow(() -> new AppException(ModerationErrorCode.MODERATION_KEYWORD_NOT_FOUND));
        keywords.delete(keyword);
        audit.enqueue("MODERATION_KEYWORD_DELETED", "MODERATION_KEYWORD", id.toString(),
                Map.of("term", keyword.getTerm(), "level", keyword.getLevel().name()), Map.of());
    }

    private static ModerationKeywordResponse response(ModerationKeyword keyword) {
        return ModerationKeywordResponse.builder().id(keyword.getId()).term(keyword.getTerm())
                .level(keyword.getLevel()).createdAt(keyword.getCreatedAt()).build();
    }
}
