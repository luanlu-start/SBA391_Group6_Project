package com.fptpost.modules.identity.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.*;
import java.util.UUID;
import com.fptpost.modules.identity.enums.KycFileType;

@Entity @Table(name = "kyc_files", uniqueConstraints = @UniqueConstraint(name = "uq_kyc_file_type", columnNames = {"kyc_id", "file_type"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class KycFile {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "public_id", nullable = false, unique = true, updatable = false, columnDefinition = "uniqueidentifier") private UUID publicId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "kyc_id", nullable = false) private KycRequest kyc;
    @Enumerated(EnumType.STRING) @Column(name = "file_type", nullable = false, length = 30) private KycFileType type;
    @Column(name = "storage_key", nullable = false, columnDefinition = "uniqueidentifier") private UUID storageKey;
    @Column(nullable = false, length = 64) private String checksum;
    @Column(name = "media_type", nullable = false, length = 30) private String mediaType;
    @Column(name = "size_bytes", nullable = false) private long sizeBytes;
}
