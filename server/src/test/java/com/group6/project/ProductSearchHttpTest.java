package com.group6.project;

import com.group6.project.common.exception.GlobalExceptionHandler;
import com.group6.project.common.response.PageResponse;
import com.group6.project.modules.product.controller.ProductController;
import com.group6.project.modules.product.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProductSearchHttpTest {
    private final ProductService service = mock(ProductService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ProductController(service))
            .setControllerAdvice(new GlobalExceptionHandler()).build();

    @Test
    void exposesStablePageResponse() throws Exception {
        when(service.getAllProducts(any())).thenReturn(new PageResponse<>(List.of(), 0, 12, 0, 0, true, true));
        mvc.perform(get("/api/v1/products")).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void rejectsInvalidPaginationSortingAndFiltersAsBadRequests() throws Exception {
        String[][] cases = {{"page", "-1"}, {"page", "abc"}, {"size", "0"}, {"size", "101"},
                {"sortBy", "secretField"}, {"direction", "SIDEWAYS"}, {"status", "UNKNOWN"}, {"minPrice", "-1"}};
        for (String[] input : cases) {
            mvc.perform(get("/api/v1/products").param(input[0], input[1]))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(1002));
        }
        mvc.perform(get("/api/v1/products").param("minPrice", "100").param("maxPrice", "10"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(1002));
        verifyNoInteractions(service);
    }

    @Test
    void rejectsMalformedUuidAndPricePrecisionBeforeCallingService() throws Exception {
        mvc.perform(get("/api/v1/products/not-a-uuid"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(1002));
        mvc.perform(post("/api/v1/products").contentType("application/json").content("""
                {"name":"Product","category":"Other","price":0.001,"stock":1}
                """))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.price").exists());
        mvc.perform(post("/api/v1/products").contentType("application/json").content("""
                {"name":"Product","category":"Other","price":0.10,"stock":1,"status":"UNKNOWN"}
                """))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.status").exists());
        verifyNoInteractions(service);
    }
}
