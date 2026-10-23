package com.resumeforge.service;

import com.resumeforge.model.Resume;
import com.resumeforge.template.PdfRenderer;
import com.resumeforge.template.TemplateType;
import org.springframework.stereotype.Service;
import java.io.IOException;

@Service
public class ResumeService {
    private final AtsValidator atsValidator = new AtsValidator();

    public byte[] renderResumeToPdf(Resume resume, TemplateType template) throws IOException {
        return renderResumeToPdf(resume, template, null);
    }

    public byte[] renderResumeToPdf(Resume resume, TemplateType template, String accent) throws IOException {
        // Validate
        AtsValidator.ValidationResult validation = atsValidator.validate(resume);
        if (!validation.valid()) {
            throw new IllegalArgumentException("Resume validation failed: " + validation.errors());
        }

        // Sanitize
        Resume sanitized = atsValidator.sanitize(resume);

        // Render
        PdfRenderer renderer = new PdfRenderer(sanitized, template, accent);
        return renderer.render();
    }

    public AtsValidator.ValidationResult validateResume(Resume resume) {
        return atsValidator.validate(resume);
    }

    public TemplateType[] getAvailableTemplates() {
        return TemplateType.values();
    }
}
