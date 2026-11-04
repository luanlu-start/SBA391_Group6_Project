package com.fptpost.modules.order.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fptpost.modules.order.dto.PricingEstimateRequest;
import com.fptpost.modules.order.dto.PricingEstimateResponse;
import com.fptpost.modules.order.model.DeliveryUrgency;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class PricingCalculatorService {

    private final ObjectMapper objectMapper;

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public PricingEstimateResponse estimatePrice(PricingEstimateRequest request) {
        // 1. Calculate distance (km) and calculation method
        CalculationResult routingResult = resolveDistance(request);
        BigDecimal distanceKm = routingResult.distanceKm;
        String calculationMethod = routingResult.method;

        // 2. Base distance fee (PRM-026, PRM-027, PRM-028)
        double distanceVal = distanceKm.doubleValue();
        double baseFee;
        if (distanceVal <= 2.0) {
            baseFee = 10000.0;
        } else if (distanceVal <= 50.0) {
            baseFee = 10000.0 + (distanceVal - 2.0) * 3500.0;
        } else {
            baseFee = 10000.0 + (48.0 * 3500.0) + (distanceVal - 50.0) * 2000.0;
        }

        // 3. Weight surcharge (PRM-029 to PRM-032)
        double weightVal = request.getWeightKg() != null ? request.getWeightKg().doubleValue() : 1.0;
        double weightSurcharge;
        if (weightVal <= 2.0) {
            weightSurcharge = 0.0;
        } else if (weightVal <= 5.0) {
            weightSurcharge = 5000.0;
        } else if (weightVal <= 10.0) {
            weightSurcharge = 12000.0;
        } else if (weightVal <= 20.0) {
            weightSurcharge = 25000.0;
        } else {
            weightSurcharge = 25000.0 + Math.ceil(weightVal - 20.0) * 2500.0;
        }

        // 4. Insurance fee (PRM-033 to PRM-035)
        double declaredVal = request.getDeclaredValue() != null ? request.getDeclaredValue().doubleValue() : 0.0;
        double insuranceFee = 0.0;
        if (declaredVal > 0.0) {
            insuranceFee = Math.min(declaredVal * 0.005, 50000.0);
        }

        // 5. Subtotal before urgency
        double subtotal = baseFee + weightSurcharge + insuranceFee;

        // 6. Urgency multiplier (PRM-036 to PRM-038)
        DeliveryUrgency urgency = request.getUrgency() != null ? request.getUrgency() : DeliveryUrgency.STANDARD;
        double multiplier = switch (urgency) {
            case SAME_DAY -> 1.2;
            case EXPRESS_2H -> 1.5;
            case STANDARD -> 1.0;
        };

        // 7. Total price & rounding (PRM-053: Ceil to nearest 500 VND, min 10,000 VND)
        double totalBeforeRounding = subtotal * multiplier;
        double finalRounded = Math.ceil(totalBeforeRounding / 500.0) * 500.0;
        if (finalRounded < 10000.0) {
            finalRounded = 10000.0;
        }

        return PricingEstimateResponse.builder()
                .distanceKm(distanceKm.setScale(2, RoundingMode.HALF_UP))
                .baseDistanceFee(BigDecimal.valueOf(baseFee).setScale(0, RoundingMode.HALF_UP))
                .weightSurcharge(BigDecimal.valueOf(weightSurcharge).setScale(0, RoundingMode.HALF_UP))
                .insuranceFee(BigDecimal.valueOf(insuranceFee).setScale(0, RoundingMode.HALF_UP))
                .urgency(urgency)
                .urgencyMultiplier(BigDecimal.valueOf(multiplier).setScale(1, RoundingMode.HALF_UP))
                .subtotalBeforeUrgency(BigDecimal.valueOf(subtotal).setScale(0, RoundingMode.HALF_UP))
                .totalEstimatedPrice(BigDecimal.valueOf(finalRounded).setScale(0, RoundingMode.HALF_UP))
                .calculationMethod(calculationMethod)
                .build();
    }

    private CalculationResult resolveDistance(PricingEstimateRequest request) {
        boolean hasCoordinates = request.getPickupLat() != null && request.getPickupLng() != null
                && request.getDeliveryLat() != null && request.getDeliveryLng() != null;

        if (hasCoordinates) {
            double pLat = request.getPickupLat().doubleValue();
            double pLng = request.getPickupLng().doubleValue();
            double dLat = request.getDeliveryLat().doubleValue();
            double dLng = request.getDeliveryLng().doubleValue();

            // Try OSRM routing first
            Double osrmDistance = fetchOsrmDistanceKm(pLat, pLng, dLat, dLng);
            if (osrmDistance != null && osrmDistance > 0.0) {
                return new CalculationResult(
                        BigDecimal.valueOf(osrmDistance).setScale(2, RoundingMode.HALF_UP),
                        "OSRM_ROUTING"
                );
            }

            // Fallback to Haversine * 1.3 road factor (BR-C-010)
            double haversineKm = calculateHaversineKm(pLat, pLng, dLat, dLng) * 1.3;
            if (haversineKm < 0.1) {
                haversineKm = 0.1;
            }
            return new CalculationResult(
                    BigDecimal.valueOf(haversineKm).setScale(2, RoundingMode.HALF_UP),
                    "HAVERSINE_FALLBACK"
            );
        }

        if (request.getDistanceKm() != null && request.getDistanceKm().compareTo(BigDecimal.ZERO) > 0) {
            return new CalculationResult(
                    request.getDistanceKm().setScale(2, RoundingMode.HALF_UP),
                    "PRESET_DISTANCE"
            );
        }

        return new CalculationResult(BigDecimal.valueOf(2.0), "DEFAULT_BASE");
    }

    private Double fetchOsrmDistanceKm(double lat1, double lng1, double lat2, double lng2) {
        try {
            // OSRM format: /route/v1/driving/{lng1},{lat1};{lng2},{lat2}?overview=false
            String url = String.format(Locale.US,
                    "https://router.project-osrm.org/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=false",
                    lng1, lat1, lng2, lat2);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "FPTPost-OrderService/1.0")
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 && response.body() != null) {
                JsonNode root = objectMapper.readTree(response.body());
                if ("Ok".equalsIgnoreCase(root.path("code").asText())) {
                    JsonNode routes = root.path("routes");
                    if (routes.isArray() && !routes.isEmpty()) {
                        double distanceMeters = routes.get(0).path("distance").asDouble(0.0);
                        if (distanceMeters > 0.0) {
                            return distanceMeters / 1000.0;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("OSRM routing request failed: {}. Falling back to Haversine formula.", e.getMessage());
        }
        return null;
    }

    private double calculateHaversineKm(double lat1, double lon1, double lat2, double lon2) {
        final double EARTH_RADIUS = 6371.0; // km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS * c;
    }

    private record CalculationResult(BigDecimal distanceKm, String method) {}
}
