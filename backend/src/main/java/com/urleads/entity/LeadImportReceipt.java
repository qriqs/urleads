package com.urleads.entity;

import java.time.OffsetDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "lead_import_receipt")
@Getter
@Setter
@NoArgsConstructor
public class LeadImportReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "preview_id", nullable = false, unique = true, length = 255)
    private String previewId;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "file_hash", nullable = false, length = 64)
    private String fileHash;

    @Column(name = "row_count", nullable = false)
    private int rowCount;

    @Column(name = "lead_ids", nullable = false, columnDefinition = "TEXT")
    private String leadIds;

    @Column(name = "confirmed_at", nullable = false)
    private OffsetDateTime confirmedAt;

    public LeadImportReceipt(String previewId, Long usuarioId, String fileHash, int rowCount, String leadIds, OffsetDateTime confirmedAt) {
        this.previewId = previewId;
        this.usuarioId = usuarioId;
        this.fileHash = fileHash;
        this.rowCount = rowCount;
        this.leadIds = leadIds;
        this.confirmedAt = confirmedAt;
    }
}
