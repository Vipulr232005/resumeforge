package com.resumeforge.template;

import com.resumeforge.model.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import java.awt.Color;
import java.io.IOException;
import java.net.URI;
import java.util.*;

public class PdfRenderer {
    private static final float MARGIN = 54;            // 0.75" classic resume margin
    private static final float NAME_SIZE = 21;
    private static final float CONTACT_SIZE = 9.5f;
    private static final float SECTION_SIZE = 11f;
    private static final float BODY_SIZE = 10.2f;
    private static final float LINE = 13.5f;           // body line height
    private static final float SECTION_GAP = 12;       // space before a section header
    private static final float AFTER_RULE = 9;         // space between header rule and content
    private static final float ENTRY_GAP = 8;          // space between entries

    private PDFont body, bold, oblique;

    private final Resume resume;
    private final TemplateType template;
    private final Color accentColor;
    private final PDDocument document;
    private PDPageContentStream cs;
    private PDPage page;
    private float y;
    private float pageW, pageH;

    public PdfRenderer(Resume resume, TemplateType template) throws IOException {
        this(resume, template, null);
    }

    public PdfRenderer(Resume resume, TemplateType template, String accent) throws IOException {
        this.resume = resume;
        this.template = template;
        Color fallback = template == TemplateType.MODERN ? new Color(37, 99, 235)
            : template == TemplateType.EXECUTIVE ? new Color(15, 118, 110) : Color.BLACK;
        this.accentColor = accent != null && accent.matches("#[0-9a-fA-F]{6}")
            ? Color.decode(accent) : fallback;
        this.document = new PDDocument();
    }

    public byte[] render() throws IOException {
        body = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        oblique = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

        newPage();
        renderHeader();

        switch (template) {
            case CHRONOLOGICAL, EXECUTIVE -> { renderExperience(); renderEducation(); renderSkills(); renderSummary(); renderProjects(); renderCertifications(); }
            case MODERN -> { renderSummary(); renderExperience(); renderSkills(); renderEducation(); renderProjects(); renderCertifications(); }
            case TECHNICAL     -> { renderSkills(); renderProjects(); renderExperience(); renderEducation(); renderSummary(); renderCertifications(); }
            default            -> { renderSummary(); renderSkills(); renderExperience(); renderEducation(); renderProjects(); renderCertifications(); }
        }

        if (cs != null) cs.close();
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        document.save(baos);
        document.close();
        return baos.toByteArray();
    }

    private void newPage() throws IOException {
        if (cs != null) cs.close();
        page = new PDPage(PDRectangle.LETTER);
        document.addPage(page);
        cs = new PDPageContentStream(document, page);
        pageW = page.getMediaBox().getWidth();
        pageH = page.getMediaBox().getHeight();
        y = pageH - MARGIN;
    }

    private void ensure(float needed) throws IOException {
        if (y - needed < MARGIN) newPage();
    }

    // ===== Header =====
    private void renderHeader() throws IOException {
        Contact c = resume.contact();
        if (c == null) return;

        if (c.fullName() != null && !c.fullName().isBlank()) {
            drawCentered(c.fullName(), y, NAME_SIZE, bold);
            y -= NAME_SIZE + 7;
        }

        List<String> parts = new ArrayList<>();
        if (c.email() != null && !c.email().isBlank()) parts.add(c.email());
        if (c.phone() != null && !c.phone().isBlank()) parts.add(c.phone());
        if (c.location() != null && !c.location().isBlank()) parts.add(c.location());
        if (!parts.isEmpty()) {
            drawCentered(String.join("   |   ", parts), y, CONTACT_SIZE, body);
            y -= CONTACT_SIZE + 10;
        }

        List<ProfileLink> profiles = new ArrayList<>();
        profileLink("LinkedIn", c.linkedin(), "linkedin.com", profiles);
        profileLink("GitHub", c.github(), "github.com", profiles);
        if (!profiles.isEmpty()) {
            float separatorWidth = width("   |   ", body, CONTACT_SIZE);
            float totalWidth = 0;
            for (ProfileLink profile : profiles) totalWidth += width(profile.labelAndUrl(), body, CONTACT_SIZE);
            totalWidth += separatorWidth * (profiles.size() - 1);
            float x = Math.max(MARGIN, (pageW - totalWidth) / 2f);
            for (int i = 0; i < profiles.size(); i++) {
                ProfileLink profile = profiles.get(i);
                float segmentWidth = width(profile.labelAndUrl(), body, CONTACT_SIZE);
                drawText(profile.labelAndUrl(), x, y, CONTACT_SIZE, body);
                addLink(x, y, segmentWidth, CONTACT_SIZE, profile.uri());
                x += segmentWidth + separatorWidth;
            }
            y -= CONTACT_SIZE + 10;
        }

        // Keep the minimal style free of divider rules; other styles use a simple text-safe rule.
        if (template != TemplateType.MINIMAL) rule(MARGIN, pageW - MARGIN, y, 1.2f);
        y -= SECTION_GAP;
    }

    private record ProfileLink(String labelAndUrl, String uri) { }

    private void profileLink(String label, String raw, String allowedHost, List<ProfileLink> links) {
        String uri = safeProfileUri(raw, allowedHost);
        if (uri != null) links.add(new ProfileLink(label + ": " + uri, uri));
    }

    private String safeProfileUri(String raw, String allowedHost) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.trim();
        String candidate = value.matches("(?i)^https?://.*") ? value : "https://" + value.replaceFirst("^/+", "");
        try {
            URI uri = URI.create(candidate);
            String host = uri.getHost();
            if ("https".equalsIgnoreCase(uri.getScheme()) && host != null
                    && (host.equalsIgnoreCase(allowedHost) || host.equalsIgnoreCase("www." + allowedHost))
                    && uri.getUserInfo() == null) {
                return uri.toASCIIString();
            }
        } catch (IllegalArgumentException ignored) {
            // Invalid profile values remain visible in the editor but do not become PDF links.
        }
        return null;
    }

    private void addLink(float x, float baseline, float width, float size, String uri) throws IOException {
        PDAnnotationLink annotation = new PDAnnotationLink();
        annotation.setRectangle(new PDRectangle(x, baseline - 2, width, size + 4));
        PDActionURI action = new PDActionURI();
        action.setURI(uri);
        annotation.setAction(action);
        page.getAnnotations().add(annotation);
    }

    // ===== Sections =====
    private void renderSummary() throws IOException {
        String s = resume.summary();
        if (s == null || s.isBlank()) return;
        sectionHeader("Summary");
        wrapped(s, MARGIN, pageW - MARGIN, BODY_SIZE, body);
        y -= ENTRY_GAP;
    }

    private void renderSkills() throws IOException {
        List<String> skills = resume.skills();
        if (skills == null || skills.isEmpty()) return;
        sectionHeader("Skills");
        wrapped(String.join("   •   ", skills), MARGIN, pageW - MARGIN, BODY_SIZE, body);
        y -= ENTRY_GAP;
    }

    private void renderExperience() throws IOException {
        List<Experience> exps = resume.experience();
        if (exps == null || exps.isEmpty()) return;
        sectionHeader("Experience");
        for (Experience e : exps) {
            ensure(LINE * 2);
            String title = e.title() == null ? "" : e.title();
            if (e.company() != null && !e.company().isBlank()) title += "  —  " + e.company();
            String dates = dateRange(e.startDate(), e.endDate());

            drawText(title, MARGIN, y, BODY_SIZE, bold);
            if (!dates.isBlank()) drawRight(dates, pageW - MARGIN, y, BODY_SIZE - 0.5f, oblique);
            y -= LINE;

            if (e.summary() != null && !e.summary().isBlank())
                wrapped(e.summary(), MARGIN, pageW - MARGIN, BODY_SIZE, body);
            if (e.bullets() != null)
                for (String b : e.bullets()) bullet(b);
            y -= ENTRY_GAP;
        }
    }

    private void renderEducation() throws IOException {
        List<Education> edus = resume.education();
        if (edus == null || edus.isEmpty()) return;
        sectionHeader("Education");
        for (Education e : edus) {
            ensure(LINE * 2);
            String title = e.degree() == null ? "" : e.degree();
            if (e.field() != null) title += (title.isEmpty() ? e.field() : " in " + e.field());
            if (e.institution() != null) title += (title.isEmpty() ? e.institution() : "  —  " + e.institution());

            if (!title.isEmpty()) {
                drawText(title, MARGIN, y, BODY_SIZE, bold);
                if (e.graduationDate() != null) drawRight(e.graduationDate(), pageW - MARGIN, y, BODY_SIZE - 0.5f, oblique);
                y -= LINE;
            }
            if (e.gpa() != null && !e.gpa().isBlank()) {
                drawText("GPA: " + e.gpa(), MARGIN, y, BODY_SIZE - 0.5f, body);
                y -= LINE;
            }
            y -= ENTRY_GAP;
        }
    }

    private void renderProjects() throws IOException {
        List<Project> projs = resume.projects();
        if (projs == null || projs.isEmpty()) return;
        sectionHeader("Projects");
        for (Project p : projs) {
            ensure(LINE * 2);
            if (p.title() != null) {
                drawText(p.title(), MARGIN, y, BODY_SIZE, bold);
                y -= LINE;
            }
            if (p.technologies() != null && !p.technologies().isEmpty()) {
                drawText("Tech: " + String.join(", ", p.technologies()), MARGIN, y, BODY_SIZE - 0.5f, oblique);
                y -= LINE;
            }
            if (p.description() != null && !p.description().isBlank())
                wrapped(p.description(), MARGIN, pageW - MARGIN, BODY_SIZE, body);
            y -= ENTRY_GAP;
        }
    }

    private void renderCertifications() throws IOException {
        List<String> certs = resume.certifications();
        if (certs == null || certs.isEmpty()) return;
        sectionHeader("Certifications");
        for (String cert : certs) bullet(cert);
        y -= ENTRY_GAP;
    }

    // ===== Primitives =====
    private void sectionHeader(String text) throws IOException {
        ensure(SECTION_SIZE + AFTER_RULE + LINE);
        y -= SECTION_GAP - 4;
        boolean caps = template == TemplateType.CHRONOLOGICAL || template == TemplateType.TECHNICAL;
        drawText(caps ? text.toUpperCase() : text, MARGIN, y, SECTION_SIZE, bold);
        y -= 5;
        // MINIMAL uses a lighter, thinner rule; others a solid rule.
        if (template != TemplateType.MINIMAL && template != TemplateType.TECHNICAL)
            rule(MARGIN, pageW - MARGIN, y, 0.9f);
        y -= AFTER_RULE;
    }

    private void bullet(String text) throws IOException {
        float textX = MARGIN + 14;
        float maxX = pageW - MARGIN;
        List<String> lines = wrap(text, maxX - textX, BODY_SIZE);
        for (int i = 0; i < lines.size(); i++) {
            ensure(LINE);
            if (i == 0) drawText("•", MARGIN + 4, y, BODY_SIZE, body);
            drawText(lines.get(i), textX, y, BODY_SIZE, body);
            y -= LINE;
        }
    }

    private void wrapped(String text, float x, float maxX, float size, PDFont font) throws IOException {
        for (String line : wrap(text, maxX - x, size)) {
            ensure(LINE);
            drawText(line, x, y, size, font);
            y -= LINE;
        }
    }

    private List<String> wrap(String text, float maxWidth, float size) throws IOException {
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : text.split("\\s+")) {
            String test = cur.isEmpty() ? word : cur + " " + word;
            if (width(test, body, size) > maxWidth && !cur.isEmpty()) {
                lines.add(cur.toString());
                cur = new StringBuilder(word);
            } else {
                cur = new StringBuilder(test);
            }
        }
        if (!cur.isEmpty()) lines.add(cur.toString());
        return lines;
    }

    private void drawText(String text, float x, float y, float size, PDFont font) throws IOException {
        cs.setFont(font, size);
        cs.beginText();
        cs.newLineAtOffset(x, y);
        cs.showText(sanitize(text));
        cs.endText();
    }

    private void drawCentered(String text, float y, float size, PDFont font) throws IOException {
        drawText(text, (pageW - width(text, font, size)) / 2f, y, size, font);
    }

    private void drawRight(String text, float rightX, float y, float size, PDFont font) throws IOException {
        drawText(text, rightX - width(text, font, size), y, size, font);
    }

    private void rule(float x1, float x2, float yy) throws IOException { rule(x1, x2, yy, 0.9f); }
    private void rule(float x1, float x2, float yy, float wgt) throws IOException {
        cs.setStrokingColor(accentColor);
        cs.setLineWidth(wgt);
        cs.moveTo(x1, yy);
        cs.lineTo(x2, yy);
        cs.stroke();
        cs.setStrokingColor(Color.BLACK);
    }

    private float width(String text, PDFont font, float size) throws IOException {
        return font.getStringWidth(sanitize(text)) / 1000f * size;
    }

    private String dateRange(String start, String end) {
        if (start == null) return "";
        return end == null ? start : start + " – " + end;
    }

    // Helvetica (WinAnsi) can't encode every glyph; replace common unicode with safe equivalents.
    private String sanitize(String s) {
        if (s == null) return "";
        return s.replace('–', '-').replace('—', '-')
                .replace('‘', '\'').replace('’', '\'')
                .replace('“', '"').replace('”', '"')
                .replaceAll("[^\\x00-\\xFF\\u2022]", "");
    }
}
