package com.resumeforge.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Resume(
    Contact contact,
    String summary,
    List<String> skills,
    List<Experience> experience,
    List<Education> education,
    List<Project> projects,
    List<String> certifications
) {
}
