package com.fptpost.modules.backoffice.service;

import com.fptpost.modules.backoffice.dto.response.AccountRequestResponse;
import com.fptpost.modules.identity.enums.AccountRequestStatus;
import com.fptpost.modules.identity.model.AccountDataRequest;
import com.fptpost.modules.identity.model.User;
import com.fptpost.modules.identity.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class AccountRequestPresenter {

    private final UserProfileRepository profiles;

    public AccountRequestResponse response(AccountDataRequest request, Instant now) {
        User user = request.getUser();
        boolean open = request.getStatus() == AccountRequestStatus.PENDING || request.getStatus() == AccountRequestStatus.PROCESSING;
        return AccountRequestResponse.builder()
                .id(request.getPublicId())
                .type(request.getType())
                .status(request.getStatus())
                .reason(request.getReason())
                .requestedAt(request.getRequestedAt())
                .dueAt(request.getDueAt())
                .overdue(open && request.getDueAt() != null && now.isAfter(request.getDueAt()))
                .userPublicId(user.getPublicId())
                .userFullName(profiles.findById(user.getId()).map(profile -> profile.getFullName()).orElse(null))
                .userEmail(user.getEmail())
                .processedAt(request.getProcessedAt())
                .resolutionNote(request.getResolutionNote())
                .build();
    }
}
