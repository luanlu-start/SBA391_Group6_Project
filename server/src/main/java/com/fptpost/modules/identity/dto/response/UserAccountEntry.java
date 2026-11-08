package com.fptpost.modules.identity.dto.response;

import java.util.UUID;

/** Directory projection keyed by internal id for modules that store the numeric user reference. */
public record UserAccountEntry(Long id, UUID publicId, String fullName, String email) {}
