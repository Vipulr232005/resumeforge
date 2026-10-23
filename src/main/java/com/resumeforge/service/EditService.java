package com.resumeforge.service;

import com.resumeforge.model.Resume;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class EditService {

    public EditResult editSection(Resume resume, String section, String newContent) {
        Resume after = switch (section.toUpperCase()) {
            case "SUMMARY" -> new Resume(
                resume.contact(), newContent, resume.skills(),
                resume.experience(), resume.education(), resume.projects(), resume.certifications()
            );
            case "SKILLS" -> {
                var skills = newContent.isEmpty() ? List.<String>of()
                    : Arrays.asList(newContent.split(",\\s*"));
                yield new Resume(
                    resume.contact(), resume.summary(), skills,
                    resume.experience(), resume.education(), resume.projects(), resume.certifications()
                );
            }
            case "CERTIFICATIONS" -> {
                var certs = newContent.isEmpty() ? List.<String>of()
                    : Arrays.asList(newContent.split(",\\s*"));
                yield new Resume(
                    resume.contact(), resume.summary(), resume.skills(),
                    resume.experience(), resume.education(), resume.projects(), certs
                );
            }
            default -> throw new IllegalArgumentException(
                "Editable sections: SUMMARY, SKILLS, CERTIFICATIONS. Got: " + section);
        };

        Map<String, Object> diff = Map.of("section", section, "before", resume, "after", after);
        return new EditResult(after, diff);
    }

    public record EditResult(
        Resume resume,
        Map<String, Object> diff
    ) {
    }
}
