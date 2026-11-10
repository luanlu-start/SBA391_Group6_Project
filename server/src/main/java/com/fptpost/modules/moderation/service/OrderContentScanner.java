package com.fptpost.modules.moderation.service;

import com.fptpost.modules.moderation.model.ModerationKeyword;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * So khớp từ khóa theo nguyên từ trên nội dung tiếng Việt đã chuẩn hóa. Cụm từ nhiều tiếng còn được so khớp
 * khi người gửi bỏ dấu (ví dụ "ma tuy"); từ đơn thì không, vì bỏ dấu dễ trùng từ vô hại ("súng" và "sung").
 */
@Component
public class OrderContentScanner {

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    public static String normalize(String value) {
        if (value == null) return "";
        String text = Normalizer.normalize(value, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        return SPACES.matcher(text).replaceAll(" ").strip();
    }

    public List<String> matches(String content, Collection<ModerationKeyword> keywords) {
        String text = normalize(content);
        String plain = stripMarks(text);
        List<String> matched = new ArrayList<>();
        for (ModerationKeyword keyword : keywords) {
            String term = normalize(keyword.getTerm());
            if (term.isEmpty()) continue;
            boolean found = contains(text, term) || term.contains(" ") && contains(plain, stripMarks(term));
            if (found && !matched.contains(term)) matched.add(term);
        }
        return matched;
    }

    private static boolean contains(String text, String term) {
        return Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(term) + "(?![\\p{L}\\p{N}])").matcher(text).find();
    }

    private static String stripMarks(String value) {
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        return MARKS.matcher(decomposed).replaceAll("").replace('đ', 'd');
    }
}
