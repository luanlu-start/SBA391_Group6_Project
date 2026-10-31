package com.fptpost.modules.catalog.config;

import com.fptpost.modules.catalog.model.ItemCategory;
import com.fptpost.modules.catalog.repository.ItemCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@Order(20)
@RequiredArgsConstructor
public class ItemCategoryDataInitializer implements CommandLineRunner {

    private final ItemCategoryRepository itemCategoryRepository;

    @Override
    public void run(String... args) {
        if (itemCategoryRepository.count() > 0) {
            return;
        }

        List<ItemCategory> defaultCategories = List.of(
                ItemCategory.builder()
                        .code("DOCUMENTS")
                        .name("Tài liệu & Hồ sơ")
                        .description("Tài liệu, hợp đồng, chứng từ, hóa đơn, bằng cấp và thư từ văn phòng.")
                        .isProhibited(false)
                        .requiresReview(false)
                        .active(true)
                        .build(),
                ItemCategory.builder()
                        .code("ELECTRONICS")
                        .name("Thiết bị điện tử & Công nghệ")
                        .description("Điện thoại, laptop, linh kiện máy bay/máy tính, phụ kiện công nghệ.")
                        .isProhibited(false)
                        .requiresReview(false)
                        .active(true)
                        .build(),
                ItemCategory.builder()
                        .code("CLOTHING")
                        .name("Quần áo & Thời trang")
                        .description("Quần áo, giày dép, túi xách, phụ kiện thời trang các loại.")
                        .isProhibited(false)
                        .requiresReview(false)
                        .active(true)
                        .build(),
                ItemCategory.builder()
                        .code("FOOD_DRY")
                        .name("Thực phẩm đóng gói & Đồ khô")
                        .description("Thực phẩm có hạn sử dụng dài, đồ khô đóng gói kín, không có mùi.")
                        .isProhibited(false)
                        .requiresReview(false)
                        .active(true)
                        .build(),
                ItemCategory.builder()
                        .code("FRAGILE")
                        .name("Đồ dễ vỡ & Thủy tinh")
                        .description("Đồ gốm sứ, thủy tinh, mỹ phẩm chai lọ, cần chèn bọc chống sốc cẩn thận.")
                        .isProhibited(false)
                        .requiresReview(false)
                        .active(true)
                        .build(),
                ItemCategory.builder()
                        .code("OTHER")
                        .name("Hàng hóa thông thường khác")
                        .description("Các loại hàng hóa tiêu dùng thông thường khác không thuộc danh mục cấm.")
                        .isProhibited(false)
                        .requiresReview(false)
                        .active(true)
                        .build()
        );

        itemCategoryRepository.saveAll(defaultCategories);
        log.info("Initialized {} default item categories.", defaultCategories.size());
    }
}
