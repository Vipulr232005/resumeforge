package com.resumeforge.service;

import com.resumeforge.model.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.*;
import java.util.regex.*;

@Service
public class ParserService {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}");
    private static final Pattern PHONE_PATTERN = Pattern.compile("\\+?\\d{1,3}[\\s.-]?\\(?\\d{3}\\)?[\\s.-]?\\d{3}[\\s.-]?\\d{4}|\\d{10}|\\b\\d{3}[\\s.-]\\d{4}\\b");
    private static final Pattern URL_LIKE = Pattern.compile("(?i)(https?://|www\\.|linkedin\\.com|github\\.com|@)");
    private static final Pattern DATE_PATTERN = Pattern.compile("(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]* \\d{4}|\\d{1,2}/\\d{1,2}/\\d{4}");
    private static final String BULLET_CHARS = "•●▪‣◦·*▸►-–—";
    // keyword -> canonical section key. Matched on short lines so messy PDF dumps still resolve.
    private static final Map<String, String> HEADER_KEYWORDS = new LinkedHashMap<>();
    static {
        HEADER_KEYWORDS.put("SUMMARY", "SUMMARY");
        HEADER_KEYWORDS.put("PROFILE", "SUMMARY");
        HEADER_KEYWORDS.put("OBJECTIVE", "SUMMARY");
        HEADER_KEYWORDS.put("ABOUT", "SUMMARY");
        HEADER_KEYWORDS.put("SKILL", "SKILLS");
        HEADER_KEYWORDS.put("COMPETENC", "SKILLS");
        HEADER_KEYWORDS.put("TECHNOLOGIES", "SKILLS");
        HEADER_KEYWORDS.put("EXPERIENCE", "EXPERIENCE");
        HEADER_KEYWORDS.put("EMPLOYMENT", "EXPERIENCE");
        HEADER_KEYWORDS.put("WORK HISTORY", "EXPERIENCE");
        HEADER_KEYWORDS.put("EDUCATION", "EDUCATION");
        HEADER_KEYWORDS.put("ACADEMIC", "EDUCATION");
        HEADER_KEYWORDS.put("QUALIFICATION", "EDUCATION");
        HEADER_KEYWORDS.put("PROJECT", "PROJECTS");
        HEADER_KEYWORDS.put("CERTIF", "CERTIFICATIONS");
        HEADER_KEYWORDS.put("LICENSE", "CERTIFICATIONS");
        HEADER_KEYWORDS.put("ACHIEVEMENT", "CERTIFICATIONS");
        HEADER_KEYWORDS.put("AWARD", "CERTIFICATIONS");
    }

    public ParseResult parseFile(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        String text;

        if (filename != null && filename.endsWith(".pdf")) {
            text = parsePdfText(file);
        } else if (filename != null && (filename.endsWith(".docx") || filename.endsWith(".doc"))) {
            text = parseDocxText(file);
        } else {
            throw new IllegalArgumentException("Unsupported file format. Please upload a PDF or DOCX file.");
        }

        if (text.trim().isEmpty()) {
            return new ParseResult(
                null,
                0.0f,
                "File appears to be empty or scanned image PDF. Please paste text directly instead."
            );
        }

        Resume resume = parseText(text);
        float confidence = calculateConfidence(resume, text);

        return new ParseResult(resume, confidence, null);
    }

    public Resume parseText(String text) {
        Map<String, List<String>> sections = extractSections(text);

        Contact contact = extractContact(text);
        String summary = extractText(sections, "SUMMARY");
        List<String> skills = extractSkills(sections);
        List<Experience> experience = extractExperience(sections);
        List<Education> education = extractEducation(sections);
        List<Project> projects = extractProjects(sections);
        List<String> certifications = extractCertifications(sections);

        return new Resume(
            contact,
            summary,
            skills,
            experience,
            education,
            projects,
            certifications
        );
    }

    private String parsePdfText(MultipartFile file) throws IOException {
        byte[] bytes = file.getBytes();
        try (PDDocument doc = org.apache.pdfbox.Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc);
        }
    }

    private String parseDocxText(MultipartFile file) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(file.getInputStream())) {
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph para : doc.getParagraphs()) {
                sb.append(para.getText()).append("\n");
            }
            return sb.toString();
        }
    }

    private Map<String, List<String>> extractSections(String text) {
        Map<String, List<String>> sections = new LinkedHashMap<>();
        String[] lines = text.split("\n");
        String currentSection = "OTHER";
        List<String> currentContent = new ArrayList<>();

        for (String line : lines) {
            if (line.trim().isEmpty()) continue;

            String matchedHeader = matchHeader(line);
            if (matchedHeader != null) {
                // Close the previous section (even if empty) and open the new one.
                sections.put(currentSection, new ArrayList<>(currentContent));
                currentContent.clear();
                currentSection = matchedHeader;

                // Capture inline content after a "Header: value" style line.
                int colon = line.indexOf(':');
                if (colon >= 0 && colon < line.length() - 1) {
                    String inline = line.substring(colon + 1).trim();
                    if (!inline.isEmpty()) {
                        currentContent.add(inline);
                    }
                }
            } else {
                currentContent.add(line);
            }
        }

        sections.put(currentSection, new ArrayList<>(currentContent));
        return sections;
    }

    /**
     * Returns the canonical section key if this line is a recognized section header,
     * otherwise null. Headers are short lines whose text contains a known section keyword,
     * so messy PDF dumps ("WORK EXPERIENCE", "Key Skills:", "TECHNICAL SKILLS") still resolve
     * while content lines ("Software Engineer - Acme", "Experienced in Java") do not.
     */
    private String matchHeader(String line) {
        String candidate = line.trim();
        int colon = candidate.indexOf(':');
        if (colon >= 0) {
            candidate = candidate.substring(0, colon).trim();
        }
        String upper = candidate.toUpperCase();

        // A header is short and has no inline content separators.
        if (upper.isEmpty() || upper.length() > 35 || upper.contains(" - ")
                || upper.contains("|") || BULLET_CHARS.indexOf(candidate.charAt(0)) >= 0) {
            return null;
        }
        // At most a few words — real headers are terse, content lines are not.
        if (candidate.split("\\s+").length > 4) {
            return null;
        }

        for (Map.Entry<String, String> e : HEADER_KEYWORDS.entrySet()) {
            if (upper.contains(e.getKey())) {
                return e.getValue();
            }
        }
        return null;
    }

    private Contact extractContact(String text) {
        String[] lines = text.split("\n");
        String fullName = null;
        String email = null;
        String phone = null;
        String location = null;
        String linkedin = null;
        String github = null;

        // Assume first non-empty line is name (heuristic)
        for (String line : lines) {
            if (!line.trim().isEmpty() && fullName == null) {
                fullName = line.trim();
                break;
            }
        }

        // Extract email
        Matcher emailMatcher = EMAIL_PATTERN.matcher(text);
        if (emailMatcher.find()) {
            email = emailMatcher.group();
        }

        // Extract phone
        Matcher phoneMatcher = PHONE_PATTERN.matcher(text);
        if (phoneMatcher.find()) {
            phone = phoneMatcher.group();
        }

        // Extract LinkedIn and GitHub URLs
        if (text.contains("linkedin")) {
            linkedin = extractUrl(text, "linkedin");
        }
        if (text.contains("github")) {
            github = extractUrl(text, "github");
        }

        location = extractLocation(lines);

        return new Contact(fullName, email, phone, location, linkedin, github, null);
    }

    /**
     * Pull a location ("Austin, TX", "Remote", "London, UK") from the contact block —
     * typically a token on the pipe-separated line holding the email/phone.
     */
    private String extractLocation(String[] lines) {
        int scan = Math.min(lines.length, 6);
        for (int i = 0; i < scan; i++) {
            String line = lines[i];
            if (line.indexOf('|') < 0 && line.indexOf('•') < 0) continue;
            for (String part : line.split("[|•]")) {
                String t = part.trim();
                if (t.isEmpty() || t.length() > 40) continue;
                if (URL_LIKE.matcher(t).find()) continue;
                if (PHONE_PATTERN.matcher(t).matches()) continue;
                // A location reads like "City, ST" or a short place name with letters.
                boolean looksLocation = t.matches(".*[A-Za-z].*")
                    && (t.contains(",") || t.split("\\s+").length <= 3)
                    && !t.matches(".*\\d{4}.*");
                if (looksLocation) return t;
            }
        }
        return null;
    }

    private String extractUrl(String text, String pattern) {
        Pattern urlPattern = Pattern.compile("(?:https?://)?(?:www\\.)?(?:linkedin\\.com|github\\.com)[^\\s]*");
        Matcher matcher = urlPattern.matcher(text);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }

    private String extractText(Map<String, List<String>> sections, String sectionName) {
        for (Map.Entry<String, List<String>> entry : sections.entrySet()) {
            if (entry.getKey().contains(sectionName)) {
                return String.join(" ", entry.getValue());
            }
        }
        return null;
    }

    private List<String> extractSkills(Map<String, List<String>> sections) {
        List<String> skills = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : sections.entrySet()) {
            if (entry.getKey().contains("SKILLS")) {
                for (String line : entry.getValue()) {
                    String cleaned = stripBullet(line);
                    if (!cleaned.isEmpty()) {
                        String[] parts = cleaned.split("[,;|•·]|\\s{2,}");
                        for (String part : parts) {
                            String skill = part.trim();
                            if (!skill.isEmpty() && skill.length() <= 40) {
                                skills.add(skill);
                            }
                        }
                    }
                }
            }
        }
        return skills;
    }

    private List<Experience> extractExperience(Map<String, List<String>> sections) {
        List<Experience> experiences = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : sections.entrySet()) {
            if (entry.getKey().contains("EXPERIENCE")) {
                List<String> lines = entry.getValue();
                ExperienceBuilder expBuilder = null;

                for (String line : lines) {
                    if (line.trim().isEmpty()) continue;

                    if (isBullet(line)) {
                        if (expBuilder == null) expBuilder = new ExperienceBuilder();
                        expBuilder.addBullet(stripBullet(line));
                    } else if (DATE_PATTERN.matcher(line).find() && expBuilder != null) {
                        expBuilder.dates(line.trim());
                    } else {
                        if (expBuilder != null) {
                            experiences.add(expBuilder.build());
                        }
                        expBuilder = new ExperienceBuilder().title(line.trim());
                    }
                }

                if (expBuilder != null) {
                    experiences.add(expBuilder.build());
                }
            }
        }
        return experiences;
    }

    private List<Education> extractEducation(Map<String, List<String>> sections) {
        List<Education> educations = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : sections.entrySet()) {
            if (entry.getKey().contains("EDUCATION")) {
                for (String line : entry.getValue()) {
                    if (!line.trim().isEmpty()) {
                        // Heuristic: try to parse degree, field, and institution
                        educations.add(new Education(null, null, line.trim(), null, null, null));
                    }
                }
            }
        }
        return educations;
    }

    private List<Project> extractProjects(Map<String, List<String>> sections) {
        List<Project> projects = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : sections.entrySet()) {
            if (entry.getKey().contains("PROJECT")) {
                for (String line : entry.getValue()) {
                    if (!line.trim().isEmpty()) {
                        projects.add(new Project(line.trim(), null, null, null, null, null, null));
                    }
                }
            }
        }
        return projects;
    }

    private List<String> extractCertifications(Map<String, List<String>> sections) {
        List<String> certs = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : sections.entrySet()) {
            if (entry.getKey().contains("CERTIFICATION")) {
                certs.addAll(entry.getValue().stream()
                    .filter(s -> !s.trim().isEmpty())
                    .map(String::trim)
                    .toList());
            }
        }
        return certs;
    }

    private boolean isBullet(String line) {
        String t = line.trim();
        return !t.isEmpty() && BULLET_CHARS.indexOf(t.charAt(0)) >= 0;
    }

    private String stripBullet(String line) {
        String t = line.trim();
        int i = 0;
        while (i < t.length() && (BULLET_CHARS.indexOf(t.charAt(i)) >= 0 || t.charAt(i) == ' ')) i++;
        return t.substring(i).trim();
    }

    private float calculateConfidence(Resume resume, String originalText) {
        float score = 1.0f;

        // Deduct for missing sections
        if (resume.contact() == null || resume.contact().email() == null) score -= 0.15f;
        if (resume.experience() == null || resume.experience().isEmpty()) score -= 0.2f;
        if (resume.education() == null || resume.education().isEmpty()) score -= 0.1f;
        if (resume.skills() == null || resume.skills().isEmpty()) score -= 0.15f;

        // Deduct if text is very short (likely scanned/image)
        if (originalText.length() < 200) score -= 0.3f;

        return Math.max(0, Math.min(1, score));
    }

    public record ParseResult(
        Resume resume,
        float confidence,
        String error
    ) {
    }

    private static class ExperienceBuilder {
        private String title;
        private String company;
        private String startDate;
        private String endDate;
        private String summary;
        private List<String> bullets = new ArrayList<>();

        ExperienceBuilder title(String title) {
            this.title = title;
            return this;
        }

        ExperienceBuilder dates(String dates) {
            String[] parts = dates.split("–|–|-");
            if (parts.length >= 1) startDate = parts[0].trim();
            if (parts.length >= 2) endDate = parts[1].trim();
            return this;
        }

        ExperienceBuilder addBullet(String bullet) {
            bullets.add(bullet);
            return this;
        }

        Experience build() {
            return new Experience(title, company, startDate, endDate, summary, bullets);
        }
    }
}
