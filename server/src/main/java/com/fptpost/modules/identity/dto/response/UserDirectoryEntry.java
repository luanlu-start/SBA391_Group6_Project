package com.fptpost.modules.identity.dto.response;

import java.util.UUID;

/** Minimal administrative directory projection; authentication and private profile fields are never selected. */
public record UserDirectoryEntry(UUID publicId, String fullName, String email) {}
