package com.fptpost.modules.moderation.config;

import com.fptpost.modules.moderation.enums.ModerationKeywordLevel;
import com.fptpost.modules.moderation.model.ModerationKeyword;
import com.fptpost.modules.moderation.repository.ModerationKeywordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** Nạp danh mục hàng cấm mặc định theo BR-C-012 khi bảng còn trống; Admin chỉnh sửa tiếp qua API. */
@Component
@RequiredArgsConstructor
public class ModerationKeywordSeeder implements ApplicationRunner {

    private static final List<String> PROHIBITED = List.of("vũ khí", "súng", "đạn", "chất nổ", "thuốc nổ", "pháo nổ",
            "pháo hoa", "ma túy", "ma tuý", "cần sa", "heroin", "thuốc phiện", "chất độc", "hàng giả", "hàng nhái",
            "tiền mặt", "động vật sống", "xăng", "bình gas");
    private static final List<String> SUSPICIOUS = List.of("pin lithium", "rượu", "thuốc lá", "hóa chất", "dao găm",
            "thực phẩm tươi sống", "đồ cổ", "vàng miếng", "trang sức", "giấy tờ có giá");

    private final ModerationKeywordRepository keywords;

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (keywords.count() > 0) return;
        Instant now = Instant.now();
        PROHIBITED.forEach(term -> keywords.save(ModerationKeyword.builder()
                .term(term).level(ModerationKeywordLevel.PROHIBITED).createdAt(now).build()));
        SUSPICIOUS.forEach(term -> keywords.save(ModerationKeyword.builder()
                .term(term).level(ModerationKeywordLevel.SUSPICIOUS).createdAt(now).build()));
    }
}
