package com.urleads.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.urleads.config.ClockConfig;
import com.urleads.dto.CreateLeadRequest;
import com.urleads.dto.ImportConfirmResponse;
import com.urleads.dto.ImportPreviewResponse;
import com.urleads.dto.ImportRowPreview;
import com.urleads.dto.LeadResponse;
import com.urleads.entity.LeadImportReceipt;
import com.urleads.entity.LeadStage;
import com.urleads.exception.LeadValidationException;
import com.urleads.exception.ResourceNotFoundException;
import com.urleads.repository.LeadImportReceiptRepository;
import com.urleads.repository.LeadRepository;
import com.urleads.service.ExcelImportParser;
import com.urleads.service.ExcelImportParser.ParsedRow;
import com.urleads.service.LeadImportService;
import com.urleads.service.LeadNormalizer;
import com.urleads.service.LeadService;
import com.urleads.service.LeadValidator;

@Service
public class LeadImportServiceImpl implements LeadImportService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$"
    );

    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int MIN_SECRET_LENGTH = 32;

    private final ExcelImportParser parser;
    private final LeadService leadService;
    private final LeadRepository leadRepository;
    private final LeadImportReceiptRepository receiptRepository;
    private final Clock clock;
    private final String tokenSecret;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public LeadImportServiceImpl(
            ExcelImportParser parser,
            @Autowired(required = false) LeadService leadService,
            @Autowired(required = false) LeadRepository leadRepository,
            @Autowired(required = false) LeadImportReceiptRepository receiptRepository,
            Clock clock,
            @Value("${urleads.import.token-secret:}") String tokenSecret) {
        this.parser = parser;
        this.leadService = leadService;
        this.leadRepository = leadRepository;
        this.receiptRepository = receiptRepository;
        this.clock = clock != null ? clock : Clock.system(ClockConfig.LIMA_ZONE);
        this.tokenSecret = (tokenSecret != null) ? tokenSecret.trim() : "";
    }

    private void checkTokenSecretConfigured() {
        if (tokenSecret == null || tokenSecret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "Configuración de clave de importación inválida o ausente: se requiere urleads.import.token-secret de al menos 32 caracteres");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ImportPreviewResponse preview(Long usuarioId, MultipartFile file) {
        if (leadRepository == null) {
            throw new IllegalStateException("LeadRepository no configurado");
        }
        checkTokenSecretConfigured();

        byte[] bytes = validateAndExtractBytes(file);
        String fileHash = calculateSha256(bytes);

        List<ParsedRow> parsedRows = parser.parse(bytes, file.getOriginalFilename());
        ValidationResult validationResult = validateRows(usuarioId, parsedRows);

        String previewId = generatePreviewToken(usuarioId, fileHash);
        boolean canConfirm = validationResult.invalidCount == 0 && !validationResult.rows.isEmpty();

        return new ImportPreviewResponse(
                previewId,
                validationResult.rows.size(),
                validationResult.validCount,
                validationResult.invalidCount,
                canConfirm,
                validationResult.rows
        );
    }

    @Override
    @Transactional
    public ImportConfirmResponse confirm(Long usuarioId, MultipartFile file, String previewId) {
        if (leadRepository == null) {
            throw new IllegalStateException("LeadRepository no configurado");
        }
        if (leadService == null) {
            throw new IllegalStateException("LeadService no configurado");
        }
        if (receiptRepository == null) {
            throw new IllegalStateException("LeadImportReceiptRepository no configurado");
        }
        checkTokenSecretConfigured();

        byte[] bytes = validateAndExtractBytes(file);
        String fileHash = calculateSha256(bytes);

        // 1. Autenticar token criptográfico y verificar pertenencia de propietario y resumen de archivo
        DecryptedToken token = authenticateAndDecryptToken(previewId, usuarioId, fileHash);

        // 2. Verificar idempotencia: si ya fue confirmado por el mismo propietario/token/digest,
        // retorna el resultado durable indefinidamente sin importar el tiempo transcurrido (Finding 1)
        Optional<LeadImportReceipt> existingReceiptOpt = receiptRepository.findByPreviewId(previewId);
        if (existingReceiptOpt.isPresent()) {
            LeadImportReceipt existingReceipt = existingReceiptOpt.get();
            if (!existingReceipt.getUsuarioId().equals(usuarioId)) {
                throw new ResourceNotFoundException("Vista previa no encontrada o no pertenece al usuario");
            }
            if (!existingReceipt.getFileHash().equalsIgnoreCase(fileHash)) {
                throw new LeadValidationException("El archivo no coincide con la vista previa confirmada");
            }
            return new ImportConfirmResponse(
                    previewId,
                    existingReceipt.getRowCount(),
                    parseLeadIds(existingReceipt.getLeadIds())
            );
        }

        // 3. Para primer uso de confirmación, validar que no hayan transcurrido más de 24 horas
        long now = Instant.now(clock).toEpochMilli();
        if (now > token.timestamp() + 24L * 3600L * 1000L) {
            throw new LeadValidationException("El token de vista previa ha expirado (validez máxima de 24 horas)");
        }

        // 4. Parsear y revalidar estrictamente el libro completo
        List<ParsedRow> parsedRows = parser.parse(bytes, file.getOriginalFilename());
        ValidationResult validationResult = validateRows(usuarioId, parsedRows);

        if (validationResult.rows.isEmpty()) {
            throw new LeadValidationException("El archivo no contiene filas de datos para importar");
        }

        if (validationResult.invalidCount > 0) {
            throw new LeadValidationException("El archivo contiene filas con errores o duplicados y no puede ser confirmado");
        }

        // 5. Inserción atómica delegando a LeadService para preservar normalización C06 y outbox C04
        List<Long> createdLeadIds = new ArrayList<>();
        for (ParsedRow row : parsedRows) {
            CreateLeadRequest req = new CreateLeadRequest();
            req.setNombre(row.nombre());
            req.setTelefono(row.telefono());
            req.setCorreo(row.correo());
            req.setService(row.service());
            req.setEtapa(row.etapa());
            req.setPriority(row.priority() != null ? row.priority() : "MEDIA");
            req.setOrigin(row.origin() != null ? row.origin() : "MANUAL");

            if (!"CERRADO".equalsIgnoreCase(row.etapa()) && row.proximoSeguimiento() != null) {
                req.setProximoSeguimiento(LocalDate.parse(row.proximoSeguimiento()));
            } else {
                req.setProximoSeguimiento(null);
            }

            LeadResponse created = leadService.crear(usuarioId, req);
            createdLeadIds.add(created.getId());
        }

        // 6. Persistir recibo durable de confirmación
        String leadIdsStr = createdLeadIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        LeadImportReceipt receipt = new LeadImportReceipt(
                previewId,
                usuarioId,
                fileHash,
                createdLeadIds.size(),
                leadIdsStr,
                Instant.now(clock).atOffset(ZoneOffset.UTC)
        );
        receiptRepository.save(receipt);

        return new ImportConfirmResponse(previewId, createdLeadIds.size(), createdLeadIds);
    }

    private byte[] validateAndExtractBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new LeadValidationException("El archivo es obligatorio y no puede estar vacío");
        }
        if (file.getSize() > ExcelImportParser.MAX_UPLOAD_SIZE) {
            throw new LeadValidationException("El archivo supera el tamaño máximo permitido de 5 MiB");
        }
        try {
            return file.getBytes();
        } catch (Exception e) {
            throw new LeadValidationException("No se pudo leer el archivo Excel o está dañado");
        }
    }

    private static class ValidationResult {
        final List<ImportRowPreview> rows;
        final int validCount;
        final int invalidCount;

        ValidationResult(List<ImportRowPreview> rows, int validCount, int invalidCount) {
            this.rows = rows;
            this.validCount = validCount;
            this.invalidCount = invalidCount;
        }
    }

    private ValidationResult validateRows(Long usuarioId, List<ParsedRow> parsedRows) {
        List<ImportRowPreview> rowPreviews = new ArrayList<>();
        Map<String, Integer> seenEmails = new HashMap<>();
        Map<String, Integer> seenPhones = new HashMap<>();

        int validCount = 0;
        int invalidCount = 0;

        for (ParsedRow row : parsedRows) {
            List<String> errors = new ArrayList<>();
            boolean isDuplicate = false;

            // 1. Nombre
            if (row.nombre() == null || row.nombre().isBlank()) {
                errors.add("El nombre es obligatorio");
            } else {
                String trimmedName = row.nombre().trim();
                if (trimmedName.length() < 2 || trimmedName.length() > 120) {
                    errors.add("El nombre debe tener entre 2 y 120 caracteres");
                }
            }

            // 2. Contacto (correo o teléfono)
            boolean hasPhone = row.telefono() != null && !row.telefono().isBlank();
            boolean hasEmail = row.correo() != null && !row.correo().isBlank();
            if (!hasPhone && !hasEmail) {
                errors.add("Debe registrar teléfono o correo");
            }

            String normalizedPhone = null;
            if (hasPhone) {
                if (row.telefono().length() > 50) {
                    errors.add("El teléfono no puede superar 50 caracteres");
                }
                normalizedPhone = LeadNormalizer.normalizePhone(row.telefono());
                if (normalizedPhone == null) {
                    errors.add("El teléfono debe contener al menos un dígito");
                }
            }

            String normalizedEmail = null;
            if (hasEmail) {
                if (row.correo().length() > 120) {
                    errors.add("El correo no puede superar 120 caracteres");
                }
                if (!EMAIL_PATTERN.matcher(row.correo().trim()).matches()) {
                    errors.add("Formato de correo electrónico no válido");
                }
                normalizedEmail = LeadNormalizer.normalizeEmail(row.correo());
            }

            // 3. Servicio
            if (row.service() != null && row.service().length() > 255) {
                errors.add("El servicio no puede superar 255 caracteres");
            }

            // 4. Etapa
            if (row.etapa() == null || row.etapa().isBlank() || !LeadStage.isValid(row.etapa().trim())) {
                errors.add("Etapa no válida: solo se permite NUEVO, EN_SEGUIMIENTO o CERRADO");
            }

            // 5. Prioridad
            if (row.priority() != null && !row.priority().isBlank()) {
                if (!LeadValidator.isValidPriority(row.priority())) {
                    errors.add("Prioridad no válida: solo se permite BAJA, MEDIA o ALTA");
                }
            }

            // 6. Origen
            if (row.origin() != null && !row.origin().isBlank()) {
                if (!LeadValidator.isValidOrigin(row.origin())) {
                    errors.add("Origen no válido: solo se permite WEB, REFERIDO o MANUAL");
                }
            }

            // 7. Próximo Seguimiento
            if (row.proximoSeguimiento() != null && !row.proximoSeguimiento().isBlank()) {
                try {
                    LocalDate.parse(row.proximoSeguimiento().trim());
                } catch (DateTimeParseException e) {
                    errors.add("Fecha de próximo seguimiento no válida, use formato YYYY-MM-DD");
                }
            }

            // 8. Fórmulas
            if (row.formulasByField() != null && !row.formulasByField().isEmpty()) {
                errors.add("No se permiten fórmulas en las celdas");
            }

            // 9. Columnas adicionales
            if (row.hasExtraColumns()) {
                errors.add("Columnas no reconocidas en la fila (máximo 8 columnas)");
            }

            // 10. Detección de duplicados dentro del libro (intra-archivo)
            if (normalizedEmail != null) {
                if (seenEmails.containsKey(normalizedEmail)) {
                    errors.add("Correo duplicado en el archivo (fila " + seenEmails.get(normalizedEmail) + ")");
                    isDuplicate = true;
                } else {
                    seenEmails.put(normalizedEmail, row.rowNumber());
                }
            }

            if (normalizedPhone != null) {
                if (seenPhones.containsKey(normalizedPhone)) {
                    errors.add("Teléfono duplicado en el archivo (fila " + seenPhones.get(normalizedPhone) + ")");
                    isDuplicate = true;
                } else {
                    seenPhones.put(normalizedPhone, row.rowNumber());
                }
            }

            // 11. Detección de duplicados contra base de datos (aislado por usuario autenticado)
            if (normalizedEmail != null) {
                if (leadRepository.existsByUsuarioIdAndCorreoNorm(usuarioId, normalizedEmail)) {
                    errors.add("Ya existe un lead con este correo");
                    isDuplicate = true;
                }
            }

            if (normalizedPhone != null) {
                if (leadRepository.existsByUsuarioIdAndTelefonoNorm(usuarioId, normalizedPhone)) {
                    errors.add("Ya existe un lead con este teléfono");
                    isDuplicate = true;
                }
            }

            String status;
            if (errors.isEmpty()) {
                status = "VALID";
                validCount++;
            } else if (isDuplicate) {
                status = "DUPLICATE";
                invalidCount++;
            } else {
                status = "INVALID";
                invalidCount++;
            }

            Map<String, String> values = new LinkedHashMap<>();
            values.put("name", row.nombre());
            values.put("phone", row.telefono());
            values.put("email", row.correo());
            values.put("service", row.service());
            values.put("stage", row.etapa());
            values.put("priority", row.priority() != null ? row.priority() : "MEDIA");
            values.put("origin", row.origin() != null ? row.origin() : "MANUAL");
            values.put("nextFollowUp", "CERRADO".equalsIgnoreCase(row.etapa()) ? null : row.proximoSeguimiento());

            rowPreviews.add(new ImportRowPreview(row.rowNumber(), status, values, errors));
        }

        return new ValidationResult(rowPreviews, validCount, invalidCount);
    }

    private String calculateSha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo SHA-256 no disponible", e);
        }
    }

    private byte[] deriveAesKey() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(tokenSecret.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo SHA-256 no disponible", e);
        }
    }

    private String generatePreviewToken(Long usuarioId, String fileHash) {
        long timestamp = Instant.now(clock).toEpochMilli();
        String payload = usuarioId + ":" + timestamp + ":" + fileHash;

        byte[] iv = new byte[GCM_IV_LENGTH];
        secureRandom.nextBytes(iv);

        try {
            SecretKeySpec secretKey = new SecretKeySpec(deriveAesKey(), "AES");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            byte[] ciphertext = cipher.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            return Base64.getUrlEncoder().withoutPadding().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("Error al generar token cifrado de vista previa", e);
        }
    }

    private record DecryptedToken(Long usuarioId, long timestamp, String fileHash) {}

    private DecryptedToken authenticateAndDecryptToken(String previewId, Long currentUserId, String currentFileHash) {
        if (previewId == null || previewId.isBlank()) {
            throw new LeadValidationException("Token de vista previa no válido");
        }

        byte[] combined;
        try {
            combined = Base64.getUrlDecoder().decode(previewId.trim());
        } catch (Exception e) {
            throw new LeadValidationException("Token de vista previa no válido");
        }

        if (combined.length < GCM_IV_LENGTH + 16 + 1) {
            throw new LeadValidationException("Token de vista previa no válido");
        }

        byte[] iv = new byte[GCM_IV_LENGTH];
        System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
        int ciphertextLen = combined.length - GCM_IV_LENGTH;

        String payload;
        try {
            SecretKeySpec secretKey = new SecretKeySpec(deriveAesKey(), "AES");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            byte[] decrypted = cipher.doFinal(combined, GCM_IV_LENGTH, ciphertextLen);
            payload = new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Falla de tag GCM o corrupción -> fallo cerrado seguro sin filtrar excepciones de criptografía
            throw new LeadValidationException("Token de vista previa no válido");
        }

        String[] parts = payload.split(":");
        if (parts.length != 3) {
            throw new LeadValidationException("Token de vista previa no válido");
        }

        Long tokenUserId;
        long tokenTimestamp;
        String tokenFileHash = parts[2];
        try {
            tokenUserId = Long.parseLong(parts[0]);
            tokenTimestamp = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            throw new LeadValidationException("Token de vista previa no válido");
        }

        // Aislamiento estricto de propietario: token perteneciente a otro usuario retorna 404 seguro
        if (!tokenUserId.equals(currentUserId)) {
            throw new ResourceNotFoundException("Vista previa no encontrada o no pertenece al usuario");
        }

        // Comparar resumen criptográfico exacto del archivo re-enviado
        if (!tokenFileHash.equalsIgnoreCase(currentFileHash)) {
            throw new LeadValidationException("El archivo no coincide con la vista previa confirmada");
        }

        return new DecryptedToken(tokenUserId, tokenTimestamp, tokenFileHash);
    }

    private List<Long> parseLeadIds(String leadIdsStr) {
        if (leadIdsStr == null || leadIdsStr.isBlank()) {
            return List.of();
        }
        return Arrays.stream(leadIdsStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::valueOf)
                .toList();
    }
}
