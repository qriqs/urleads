package com.urleads.service;

import org.springframework.web.multipart.MultipartFile;
import com.urleads.dto.ImportConfirmResponse;
import com.urleads.dto.ImportPreviewResponse;

public interface LeadImportService {

    ImportPreviewResponse preview(Long usuarioId, MultipartFile file);

    ImportConfirmResponse confirm(Long usuarioId, MultipartFile file, String previewId);
}
