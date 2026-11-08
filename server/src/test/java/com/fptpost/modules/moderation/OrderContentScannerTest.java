package com.fptpost.modules.moderation;

import com.fptpost.modules.moderation.enums.ModerationKeywordLevel;
import com.fptpost.modules.moderation.model.ModerationKeyword;
import com.fptpost.modules.moderation.service.OrderContentScanner;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderContentScannerTest {

    private final OrderContentScanner scanner = new OrderContentScanner();

    private static List<ModerationKeyword> keywords(String... terms) {
        return java.util.Arrays.stream(terms)
                .map(term -> ModerationKeyword.builder().term(term).level(ModerationKeywordLevel.PROHIBITED).build())
                .toList();
    }

    @Test
    void matchesWholeWordsIgnoringCaseAndSpacing() {
        assertThat(scanner.matches("Bộ SÚNG   nước đồ chơi", keywords("súng"))).containsExactly("súng");
        assertThat(scanner.matches("Cuộc sống sung túc", keywords("súng"))).isEmpty();
        assertThat(scanner.matches("Đạo cụ sân khấu", keywords("đạn"))).isEmpty();
        assertThat(scanner.matches("Hộp đạn mô hình", keywords("đạn"))).containsExactly("đạn");
    }

    @Test
    void phrasesAreAlsoMatchedWithoutDiacriticsButSingleWordsAreNot() {
        assertThat(scanner.matches("goi hang ma tuy", keywords("ma túy"))).containsExactly("ma túy");
        assertThat(scanner.matches("trai sung chin", keywords("súng"))).isEmpty();
    }

    @Test
    void reportsEachTermOnceInKeywordOrder() {
        assertThat(scanner.matches("Xăng, pháo hoa và xăng dự phòng", keywords("pháo hoa", "xăng", "tiền mặt")))
                .containsExactly("pháo hoa", "xăng");
    }
}
