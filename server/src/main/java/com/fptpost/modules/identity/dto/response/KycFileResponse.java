package com.fptpost.modules.identity.dto.response;

import java.util.UUID;
import com.fptpost.modules.identity.enums.KycFileType;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class KycFileResponse {
    UUID id;
    KycFileType type;
    String mediaType;
    long sizeBytes;
}
