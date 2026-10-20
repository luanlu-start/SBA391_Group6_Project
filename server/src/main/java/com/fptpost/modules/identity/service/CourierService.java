package com.fptpost.modules.identity.service;

import com.fptpost.common.exception.*;
import com.fptpost.modules.identity.dto.request.*;
import com.fptpost.modules.identity.dto.response.*;
import com.fptpost.modules.identity.dto.internal.CourierRegistrationResult;
import com.fptpost.modules.identity.enums.*;
import com.fptpost.modules.identity.exception.IdentityErrorCode;
import com.fptpost.modules.identity.mapper.CourierMapper;
import com.fptpost.modules.identity.model.*;
import com.fptpost.modules.identity.repository.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class CourierService {
    private static final String COMMITMENT_VERSION = "courier-v1";
    private final IdentityUserAccess userAccess;
    private final CourierProfileRepository courierRepository;
    private final UserProfileRepository profileRepository;
    private final KycRequestRepository kycRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final ConsentRepository consentRepository;
    private final CourierMapper mapper;
    private final AuthenticationService authenticationService;
    private final Clock identityClock;

    @Transactional(readOnly = true)
    public CourierRegistrationStatusResponse status(UUID owner) {
        var user = userAccess.read(owner);
        var kyc = kycRepository.findFirstByUserIdOrderBySubmittedAtDescIdDesc(user.getId());
        LocalDate today = LocalDate.ofInstant(identityClock.instant(), ZoneId.of("Asia/Ho_Chi_Minh"));
        var profile = courierRepository.findById(user.getId()).map(mapper::toResponse).orElse(null);
        boolean eligible = kyc.filter(request -> request.getStatus() == KycStatus.APPROVED && !request.getExpiryDate().isBefore(today)
                && !request.getDob().plusYears(18).isAfter(today)).isPresent();
        return CourierRegistrationStatusResponse.builder().profile(profile).kycStatus(kyc.map(KycRequest::getStatus).orElse(KycStatus.NOT_SUBMITTED))
                .eligible(eligible).commitmentVersion(COMMITMENT_VERSION).build();
    }

    @Transactional
    public CourierRegistrationResult register(UUID owner, UUID session, CourierRegistrationRequest input, String ip, String userAgent) {
        var user = userAccess.lock(owner, session);
        if (courierRepository.existsById(user.getId())) throw new AppException(IdentityErrorCode.COURIER_ALREADY_REGISTERED);
        if (!Boolean.TRUE.equals(input.getCommitmentAccepted())) throw new AppException(GlobalErrorCode.INVALID_REQUEST);
        if (!COMMITMENT_VERSION.equals(input.getCommitmentVersion())) throw new AppException(IdentityErrorCode.COURIER_COMMITMENT_CHANGED);
        var kyc = kycRepository.findFirstByUserIdOrderBySubmittedAtDescIdDesc(user.getId()).filter(request -> request.getStatus() == KycStatus.APPROVED)
                .orElseThrow(() -> new AppException(IdentityErrorCode.COURIER_KYC_REQUIRED));
        LocalDate today = LocalDate.ofInstant(identityClock.instant(), ZoneId.of("Asia/Ho_Chi_Minh"));
        if (kyc.getExpiryDate().isBefore(today)) throw new AppException(IdentityErrorCode.COURIER_DOCUMENT_EXPIRED);
        var profile = profileRepository.findById(user.getId()).orElseThrow();
        if (profile.getDob() == null || profile.getDob().plusYears(18).isAfter(today)) throw new AppException(IdentityErrorCode.PROFILE_AGE_INVALID);
        if (!profile.getDob().equals(kyc.getDob()) || !profile.getFullName().equals(kyc.getFullName())) throw new AppException(IdentityErrorCode.KYC_PROFILE_MISMATCH);
        validateHours(input.getAvailableHours());
        var areas = input.getAreas().stream().map(value -> value.strip().replaceAll("\\s+", " ")).toList();
        if (areas.stream().map(value -> value.toLowerCase(Locale.ROOT)).distinct().count() != areas.size()) throw new AppException(IdentityErrorCode.COURIER_AREAS_INVALID);
        var saved = courierRepository.saveAndFlush(CourierProfile.builder().user(user).vehicleType(input.getVehicleType()).areas(new ArrayList<>(areas))
                .availableHours(input.getAvailableHours().stream().map(value -> AvailabilityWindow.builder().day(value.getDay()).start(value.getStart()).end(value.getEnd()).build()).toList())
                .tier(CourierTier.NEW).trustScore(new BigDecimal("50.00")).completedOrders(0).commitmentVersion(COMMITMENT_VERSION).registeredAt(identityClock.instant()).build());
        user.getRoles().add(roleRepository.findByCode("COURIER").orElseThrow()); user.setUpdatedAt(identityClock.instant()); userRepository.saveAndFlush(user);
        consentRepository.save(Consent.builder().user(user).policyType("COURIER_COMMITMENT").policyVersion(COMMITMENT_VERSION)
                .acceptedAt(identityClock.instant()).ip(ip.length() <= 45 ? ip : "unknown").build());
        // The refreshed JWT must include the newly persisted roles; the response also replaces the refresh cookie.
        var authentication = authenticationService.renewAfterRoleChange(owner, session, ip, userAgent);
        return new CourierRegistrationResult(mapper.toResponse(saved), authentication);
    }
    private void validateHours(List<AvailabilityWindowRequest> hours) {
        for (var slot : hours) if (!slot.getStart().isBefore(slot.getEnd()) || slot.getStart().getSecond() != 0 || slot.getEnd().getSecond() != 0)
            throw new AppException(IdentityErrorCode.COURIER_HOURS_INVALID);
        for (int i = 0; i < hours.size(); i++) for (int j = i + 1; j < hours.size(); j++) {
            var left = hours.get(i); var right = hours.get(j);
            if (left.getDay() == right.getDay() && left.getStart().isBefore(right.getEnd()) && right.getStart().isBefore(left.getEnd()))
                throw new AppException(IdentityErrorCode.COURIER_HOURS_INVALID);
        }
    }
}
