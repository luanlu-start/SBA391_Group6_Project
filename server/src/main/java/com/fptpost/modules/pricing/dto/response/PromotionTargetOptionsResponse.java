package com.fptpost.modules.pricing.dto.response;

import java.util.List;

public record PromotionTargetOptionsResponse(List<PromotionResponse.TargetUser> options, boolean hasMore) {}
