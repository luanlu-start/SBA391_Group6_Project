package com.fptpost.modules.identity.service;

import com.fptpost.common.exception.*;
import com.fptpost.common.file.dto.ValidatedImage;
import com.fptpost.common.file.service.*;
import com.fptpost.modules.identity.dto.request.*;
import com.fptpost.modules.identity.dto.response.*;
import com.fptpost.modules.identity.enums.*;
import com.fptpost.modules.identity.exception.IdentityErrorCode;
import com.fptpost.modules.identity.mapper.*;
import com.fptpost.modules.identity.model.*;
import com.fptpost.modules.identity.repository.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service @RequiredArgsConstructor
public class ProfileService {
    private final IdentityUserAccess userAccess;
    private final UserProfileRepository profileRepository;
    private final AddressRepository addressRepository;
    private final AccountDataRequestRepository dataRequestRepository;
    private final UserMapper userMapper;
    private final ProfileMapper profileMapper;
    private final PrivateFileStorage fileStorage;
    private final ImageValidator imageValidator;
    private final Clock identityClock;

    @Transactional
    public UserResponse update(UUID owner, UUID session, ProfileUpdateRequest request) {
        User user = userAccess.lock(owner, session);
        var profile = profileRepository.findById(user.getId()).orElseThrow();
        LocalDate today = LocalDate.ofInstant(identityClock.instant(), ZoneId.of("Asia/Ho_Chi_Minh"));
        if (request.getDob() != null && request.getDob().plusYears(18).isAfter(today)) throw new AppException(IdentityErrorCode.PROFILE_AGE_INVALID);
        profile.setFullName(request.getFullName().strip().replaceAll("\\s+", " "));
        profile.setDob(request.getDob()); profile.setBio(trim(request.getBio())); profile.setEmergencyContact(trim(request.getEmergencyContact()));
        user.setUpdatedAt(identityClock.instant());
        profileRepository.saveAndFlush(profile);
        return userMapper.toUserResponse(user, profile);
    }
    @Transactional
    public UserResponse updateAvatar(UUID owner, UUID session, MultipartFile file) {
        User user = userAccess.lock(owner, session);
        var profile = profileRepository.findById(user.getId()).orElseThrow();
        var image = imageValidator.validate(file);
        UUID old = profile.getAvatarStorageKey();
        profile.setAvatarStorageKey(fileStorage.put(image.bytes())); profile.setAvatarContentType(image.mediaType());
        profileRepository.saveAndFlush(profile); fileStorage.deleteAfterCommit(old);
        return userMapper.toUserResponse(user, profile);
    }
    @Transactional(readOnly = true)
    public ValidatedImage avatar(UUID owner) {
        var profile = profileRepository.findById(userAccess.read(owner).getId()).orElseThrow();
        if (profile.getAvatarStorageKey() == null) throw new AppException(GlobalErrorCode.RESOURCE_NOT_FOUND);
        return new ValidatedImage(fileStorage.read(profile.getAvatarStorageKey()), profile.getAvatarContentType());
    }
    @Transactional(readOnly = true)
    public List<AddressResponse> addresses(UUID owner) {
        return addressRepository.findByUserIdOrderByIdAsc(userAccess.read(owner).getId()).stream().map(profileMapper::toAddressResponse).toList();
    }
    @Transactional
    public AddressResponse saveAddress(UUID owner, UUID session, UUID id, AddressRequest request) {
        User user = userAccess.lock(owner, session);
        var all = addressRepository.findByUserIdOrderByIdAsc(user.getId());
        Address address = id == null ? Address.builder().publicId(UUID.randomUUID()).user(user).build()
                : all.stream().filter(item -> item.getPublicId().equals(id)).findFirst().orElseThrow(() -> new AppException(GlobalErrorCode.RESOURCE_NOT_FOUND));
        address.setLabel(request.getLabel().strip()); address.setAddressText(request.getAddressText().strip());
        address.setLat(request.getLat()); address.setLng(request.getLng());
        if (Boolean.TRUE.equals(request.getIsDefault()) || all.isEmpty()) {
            all.forEach(item -> item.setDefaultAddress(false)); address.setDefaultAddress(true);
        } else if (address.isDefaultAddress()) {
            var next = all.stream().filter(item -> !item.getPublicId().equals(address.getPublicId())).findFirst();
            if (next.isPresent()) { address.setDefaultAddress(false); next.get().setDefaultAddress(true); }
        }
        addressRepository.saveAll(all);
        return profileMapper.toAddressResponse(addressRepository.saveAndFlush(address));
    }
    @Transactional
    public void deleteAddress(UUID owner, UUID session, UUID id) {
        User user = userAccess.lock(owner, session);
        var address = addressRepository.findByPublicIdAndUserId(id, user.getId()).orElseThrow(() -> new AppException(GlobalErrorCode.RESOURCE_NOT_FOUND));
        addressRepository.delete(address); addressRepository.flush();
        if (address.isDefaultAddress()) addressRepository.findByUserIdOrderByIdAsc(user.getId()).stream().findFirst()
                .ifPresent(next -> { next.setDefaultAddress(true); addressRepository.save(next); });
    }
    @Transactional(readOnly = true)
    public List<AccountDataRequestResponse> dataRequests(UUID owner) {
        return dataRequestRepository.findByUserIdOrderByRequestedAtDescIdDesc(userAccess.read(owner).getId()).stream()
                .map(profileMapper::toDataRequestResponse).toList();
    }
    @Transactional
    public AccountDataRequestResponse requestData(UUID owner, UUID session, AccountRequestType type, String reason) {
        User user = userAccess.lock(owner, session);
        var pending = dataRequestRepository.findFirstByUserIdAndTypeAndStatusInOrderByRequestedAtDescIdDesc(user.getId(), type,
                List.of(AccountRequestStatus.PENDING, AccountRequestStatus.PROCESSING));
        if (pending.isPresent()) return profileMapper.toDataRequestResponse(pending.get());
        Instant now = identityClock.instant();
        return profileMapper.toDataRequestResponse(dataRequestRepository.saveAndFlush(AccountDataRequest.builder()
                .publicId(UUID.randomUUID()).user(user).type(type).status(AccountRequestStatus.PENDING).reason(trim(reason))
                .requestedAt(now).dueAt(type == AccountRequestType.ACCOUNT_DELETION ? now.plus(Duration.ofDays(30)) : null).build()));
    }
    private String trim(String value) { return value == null || value.isBlank() ? null : value.strip(); }
}
