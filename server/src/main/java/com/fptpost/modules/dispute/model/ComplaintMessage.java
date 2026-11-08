package com.fptpost.modules.dispute.model;

import com.fptpost.modules.dispute.enums.ComplaintRole;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/** Trao đổi ba bên trong khiếu nại (DB-040, BR-F-096); tin của hệ thống không có tác giả. */
@Entity
@Table(name = "complaint_messages", indexes = {
        @Index(name = "idx_complaint_messages_complaint", columnList = "complaint_id, created_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplaintMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false, columnDefinition = "uniqueidentifier")
    private UUID publicId;

    @Column(name = "complaint_id", nullable = false, updatable = false)
    private Long complaintId;

    @Column(name = "author_id", updatable = false)
    private Long authorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "author_role", nullable = false, length = 20, updatable = false)
    private ComplaintRole authorRole;

    @Column(length = 2000, columnDefinition = "nvarchar(2000)", updatable = false)
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "datetime2(6)")
    private Instant createdAt;
}
