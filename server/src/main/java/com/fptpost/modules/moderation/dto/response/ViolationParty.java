package com.fptpost.modules.moderation.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.UUID;

public record ViolationParty(@JsonIgnore Long id, UUID publicId, String fullName, String email) {
}
