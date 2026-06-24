package com.smartquiz.service;

import com.smartquiz.dto.MCQDTO;
import java.util.List;

public class PartialUploadException extends RuntimeException {
    private final List<MCQDTO.Response> imported;
    private final List<String> errors;

    public PartialUploadException(List<MCQDTO.Response> imported, List<String> errors) {
        super("Partial upload");
        this.imported = imported;
        this.errors = errors;
    }

    public List<MCQDTO.Response> getImported() { return imported; }
    public List<String> getErrors() { return errors; }
}