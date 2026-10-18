package com.urleads.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.urleads.entity.LeadImportReceipt;

@Repository
public interface LeadImportReceiptRepository extends JpaRepository<LeadImportReceipt, Long> {

    Optional<LeadImportReceipt> findByPreviewId(String previewId);

    Optional<LeadImportReceipt> findByPreviewIdAndUsuarioId(String previewId, Long usuarioId);
}
