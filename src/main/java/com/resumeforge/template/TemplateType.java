package com.resumeforge.template;

public enum TemplateType {
    MINIMAL("Minimal - Clean, generous whitespace"),
    PROFESSIONAL("Professional - Bold headers, subtle dividers"),
    CHRONOLOGICAL("Chronological - Strict reverse-chronological, skills-first"),
    TECHNICAL("Technical - Skills matrix, project-heavy"),
    MODERN("Modern - Blue accent rules, reverse-chronological"),
    EXECUTIVE("Executive - Teal accent rules, experience-first");

    private final String description;

    TemplateType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
