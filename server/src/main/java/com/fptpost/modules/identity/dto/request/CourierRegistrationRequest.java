package com.fptpost.modules.identity.dto.request;

import com.fptpost.modules.identity.enums.VehicleType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CourierRegistrationRequest {
    @NotNull private VehicleType vehicleType;
    @NotEmpty @Size(max = 10) private List<@NotBlank @Size(max = 80) String> areas;
    @NotEmpty @Size(max = 14) private List<@NotNull @Valid AvailabilityWindowRequest> availableHours;
    @NotNull @AssertTrue(message = "COURIER_COMMITMENT_REQUIRED") private Boolean commitmentAccepted;
    @NotBlank @Size(max = 50) private String commitmentVersion;
}
