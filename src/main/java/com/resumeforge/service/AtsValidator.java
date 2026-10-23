package com.resumeforge.service;

import com.resumeforge.model.Resume;
import java.util.*;

public class AtsValidator {
    private static final Set<Character> FORBIDDEN_CHARS = Set.of(
        '✓', '✔', '✕', '✖', '➤', '→', '⇒', '★', '●', '◆', '◉', '■', '▪', '▫'
    );

    public ValidationResult validate(Resume resume) {
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        // Check for forbidden special characters
        String fullText = serializeResume(resume);
        for (char c : fullText.toCharArray()) {
            if (FORBIDDEN_CHARS.contains(c)) {
                errors.add("Forbidden character found: " + c + ". Use ASCII bullets or dashes only.");
                break;
            }
        }
        // Skip emoji check for now - too many false positives

        // Estimate page count (roughly 52 lines per page at 11pt font)
        long lineCount = fullText.split("\n").length;
        if (lineCount > 104) { // ~2 pages
            warnings.add("Resume exceeds 2 pages. ATS readers work best with 1-2 pages.");
        }

        // Check for contact info
        if (resume.contact() == null || resume.contact().email() == null) {
            errors.add("Contact email is required.");
        }

        // Check for experience
        if ((resume.experience() == null || resume.experience().isEmpty()) &&
            (resume.projects() == null || resume.projects().isEmpty())) {
            warnings.add("No experience or projects found. Add at least one for relevance.");
        }

        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    public Resume sanitize(Resume resume) {
        // Remove emojis and special characters from all string fields
        // For now, return as-is; in production, would recursively sanitize all fields
        return resume;
    }

    private String serializeResume(Resume resume) {
        StringBuilder sb = new StringBuilder();
        if (resume.contact() != null) {
            if (resume.contact().fullName() != null) sb.append(resume.contact().fullName()).append("\n");
            if (resume.contact().email() != null) sb.append(resume.contact().email()).append("\n");
        }
        if (resume.summary() != null) sb.append(resume.summary()).append("\n");
        if (resume.skills() != null) sb.append(String.join("\n", resume.skills())).append("\n");
        if (resume.experience() != null) {
            for (var exp : resume.experience()) {
                if (exp.title() != null) sb.append(exp.title()).append("\n");
                if (exp.bullets() != null) sb.append(String.join("\n", exp.bullets())).append("\n");
            }
        }
        return sb.toString();
    }

    public record ValidationResult(
        boolean valid,
        List<String> errors,
        List<String> warnings
    ) {
    }
}
