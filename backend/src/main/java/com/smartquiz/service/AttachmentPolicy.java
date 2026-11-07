package com.smartquiz.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.tika.Tika;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.smartquiz.exception.BusinessRuleException;

/**
 * Allow-list for chat attachments. The type is detected from the file's bytes (Apache Tika), so a renamed
 * executable is rejected even if the browser labels it text/plain. The stored type is always the detected one.
 */
@Component
public class AttachmentPolicy {

    private static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String PPTX = "application/vnd.openxmlformats-officedocument.presentationml.presentation";
    private static final Set<String> OOXML_CONTAINERS = Set.of("application/x-tika-ooxml", "application/zip");

    /** extension -> canonical content type served back to clients. */
    private static final Map<String, String> CANONICAL_TYPES = Map.ofEntries(
            Map.entry("txt", "text/plain"),
            Map.entry("csv", "text/csv"),
            Map.entry("pdf", "application/pdf"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"),
            Map.entry("docx", DOCX),
            Map.entry("xlsx", XLSX),
            Map.entry("pptx", PPTX));

    private final Tika tika = new Tika();

    /** Validates the upload and returns the content type to store. */
    public String validate(MultipartFile file, String fileName) {
        String extension = extensionOf(fileName);
        String canonical = CANONICAL_TYPES.get(extension);
        if (canonical == null) {
            throw new BusinessRuleException("File type ." + extension + " is not allowed. Allowed: "
                    + String.join(", ", CANONICAL_TYPES.keySet().stream().sorted().toList()));
        }

        String detected;
        try (InputStream in = file.getInputStream()) {
            detected = tika.detect(in, fileName);
        } catch (IOException ex) {
            throw new BusinessRuleException("Could not read the attachment");
        }

        if (!matches(extension, canonical, detected)) {
            throw new BusinessRuleException("Attachment content does not match its ." + extension + " extension");
        }
        return canonical;
    }

    private boolean matches(String extension, String canonical, String detected) {
        if (detected == null) {
            return false;
        }
        String type = detected.toLowerCase(Locale.ROOT);
        return switch (extension) {
            case "txt", "csv" -> type.equals("text/plain") || type.equals("text/csv");
            case "docx", "xlsx", "pptx" -> type.equals(canonical) || OOXML_CONTAINERS.contains(type);
            default -> type.equals(canonical);
        };
    }

    private String extensionOf(String fileName) {
        int dot = fileName == null ? -1 : fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
