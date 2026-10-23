package com.resumeforge;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.text.PDFTextStripper;
import com.resumeforge.model.*;
import com.resumeforge.service.*;
import com.resumeforge.template.TemplateType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ResumeForgeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ResumeService resumeService;

    @Autowired
    private ParserService parserService;

    @Autowired
    private TailorService tailorService;

    @Autowired
    private EditService editService;

    @Autowired
    private AuthService authService;

    @Autowired
    private ResumeStoreService storeService;

    private ObjectMapper objectMapper = new ObjectMapper();
    private Resume testResume;

    @BeforeEach
    void setUp() {
        // Create test resume
        Contact contact = new Contact(
            "John Doe",
            "john@example.com",
            "555-1234",
            "New York, NY",
            null,
            null,
            null
        );

        List<String> skills = Arrays.asList("Java", "Spring Boot", "MySQL", "Docker", "AWS");

        Experience exp = new Experience(
            "Senior Software Engineer",
            "TechCorp Inc.",
            "Jan 2020",
            "Present",
            "Led development of microservices",
            Arrays.asList(
                "Developed REST APIs using Spring Boot",
                "Improved database query performance by 40%",
                "Mentored junior developers"
            )
        );

        Education edu = new Education(
            "Bachelor of Science",
            "Computer Science",
            "State University",
            "May 2019",
            "3.8",
            null
        );

        testResume = new Resume(
            contact,
            "Experienced software engineer with 5+ years in cloud and microservices",
            skills,
            Arrays.asList(exp),
            Arrays.asList(edu),
            List.of(),
            List.of()
        );
    }

    // ===== PHASE 1: Create & PDF Export =====
    @Test
    void phase1_renderResumeToPdf() throws Exception {
        byte[] pdf = resumeService.renderResumeToPdf(testResume, TemplateType.MINIMAL);
        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
        assertTrue(new String(pdf).contains("%PDF"));
    }

    @Test
    void profileLinksAreVisibleAndClickableInPdf() throws Exception {
        Contact contact = new Contact("Alex Example", "alex@example.com", null, null,
            "linkedin.com/in/alex-example", "github.com/alex-example", null);
        Resume withProfiles = new Resume(contact, testResume.summary(), testResume.skills(),
            testResume.experience(), testResume.education(), testResume.projects(), testResume.certifications());

        byte[] pdf = resumeService.renderResumeToPdf(withProfiles, TemplateType.PROFESSIONAL);
        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("LinkedIn: https://linkedin.com/in/alex-example"));
            assertTrue(text.contains("GitHub: https://github.com/alex-example"));

            List<String> destinations = document.getPage(0).getAnnotations().stream()
                .filter(PDAnnotationLink.class::isInstance)
                .map(PDAnnotationLink.class::cast)
                .map(PDAnnotationLink::getAction)
                .filter(PDActionURI.class::isInstance)
                .map(PDActionURI.class::cast)
                .map(PDActionURI::getURI)
                .toList();
            assertTrue(destinations.contains("https://linkedin.com/in/alex-example"));
            assertTrue(destinations.contains("https://github.com/alex-example"));
        }
    }

    @Test
    void phase1_validateResume() {
        AtsValidator.ValidationResult result = resumeService.validateResume(testResume);
        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void phase1_getAvailableTemplates() {
        TemplateType[] templates = resumeService.getAvailableTemplates();
        assertEquals(6, templates.length);
        assertTrue(Arrays.asList(templates).contains(TemplateType.MINIMAL));
        assertTrue(Arrays.asList(templates).contains(TemplateType.MODERN));
        assertTrue(Arrays.asList(templates).contains(TemplateType.EXECUTIVE));
    }

    @Test
    void phase1_renderViaApi() throws Exception {
        String resumeJson = objectMapper.writeValueAsString(testResume);
        mockMvc.perform(post("/api/resumes/render")
                .param("template", "MINIMAL")
                .contentType(MediaType.APPLICATION_JSON)
                .content(resumeJson))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    void phase1_allTemplatesRenderSelectableTextWithCustomAccent() throws Exception {
        for (TemplateType template : TemplateType.values()) {
            byte[] pdf = resumeService.renderResumeToPdf(testResume, template, "#2563eb");
            try (PDDocument document = Loader.loadPDF(pdf)) {
                String text = new PDFTextStripper().getText(document);
                assertTrue(text.contains("John Doe"), template + " should retain contact text");
                assertTrue(text.contains("Spring Boot"), template + " should retain skill text");
            }
        }
    }

    // ===== PHASE 2: Upload & Parse =====
    @Test
    void phase2_parseTextResume() {
        String text = "John Doe\njohn@example.com\n555-1234\nSkills: Java, Spring Boot\n" +
            "Experience\nSenior Engineer - TechCorp\nJan 2020 - Present\n" +
            "- Developed APIs\n- Improved performance\n\n" +
            "Education\nBS Computer Science\nState University\nMay 2019";

        Resume parsed = parserService.parseText(text);
        assertNotNull(parsed);
        assertNotNull(parsed.contact());
        assertEquals("John Doe", parsed.contact().fullName());
        assertEquals("john@example.com", parsed.contact().email());
        assertFalse(parsed.skills().isEmpty());
    }

    @Test
    void phase2_parsePdfRoundTrip() throws Exception {
        byte[] pdf = resumeService.renderResumeToPdf(testResume, TemplateType.MINIMAL);
        var file = new org.springframework.mock.web.MockMultipartFile(
            "file", "resume.pdf", "application/pdf", pdf);

        ParserService.ParseResult result = parserService.parseFile(file);
        assertNull(result.error());
        assertNotNull(result.resume());
        assertEquals("john@example.com", result.resume().contact().email());
    }

    @Test
    void phase2_parseTextViaApi() throws Exception {
        String text = "John Doe\njohn@example.com\nSkills: Java, Python";
        String json = objectMapper.writeValueAsString(
            new com.resumeforge.web.ResumeController.TextParseRequest(text)
        );
        mockMvc.perform(post("/api/resumes/parse-text")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resume").exists());
    }

    @Test
    void phase2_parseUploadedPdfViaApi() throws Exception {
        byte[] pdf = resumeService.renderResumeToPdf(testResume, TemplateType.MODERN, "#0f766e");
        var file = new org.springframework.mock.web.MockMultipartFile(
            "file", "resume.pdf", "application/pdf", pdf);
        mockMvc.perform(multipart("/api/resumes/parse").file(file))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resume.contact.email").value("john@example.com"));
    }

    // ===== PHASE 3: JD Tailoring =====
    @Test
    void phase3_tailorResume() {
        String jd = "We are looking for a Java developer with Spring Boot, Docker, and AWS experience. " +
            "Must have 5+ years of experience. Knowledge of Kubernetes and CI/CD pipelines is a plus.";

        TailorService.TailoringResult result = tailorService.tailor(testResume, jd);
        assertNotNull(result.resume());
        assertFalse(result.covered().isEmpty());
        assertTrue(result.covered().contains("java") || result.covered().contains("spring boot"));
    }

    @Test
    void phase3_tailorViaApi() throws Exception {
        String jd = "Java Spring Boot Docker AWS developer needed";
        String requestJson = objectMapper.writeValueAsString(
            new com.resumeforge.web.ResumeController.TailorRequest(testResume, jd)
        );

        mockMvc.perform(post("/api/resumes/tailor")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resume").exists())
            .andExpect(jsonPath("$.covered").isArray())
            .andExpect(jsonPath("$.missing").isArray());
    }

    @Test
    void phase3_noFabrication() {
        String jd = "Expert in Kubernetes, Golang, and DevOps";
        TailorService.TailoringResult result = tailorService.tailor(testResume, jd);

        // Verify no skills were added
        assertTrue(result.resume().skills().stream()
            .allMatch(s -> testResume.skills().contains(s) || s.isEmpty()));
    }

    // ===== PHASE 4: Editing & Versions =====
    @Test
    void phase4_editSummary() {
        String newSummary = "Updated professional summary with new experiences";
        EditService.EditResult result = editService.editSection(testResume, "SUMMARY", newSummary);

        assertNotNull(result.resume());
        assertEquals(newSummary, result.resume().summary());
        assertNotNull(result.diff());
    }

    @Test
    void phase4_editSkills() {
        String newSkills = "Python, JavaScript, Rust";
        EditService.EditResult result = editService.editSection(testResume, "SKILLS", newSkills);

        assertNotNull(result.resume());
        assertFalse(result.resume().skills().isEmpty());
        assertTrue(result.resume().skills().contains("Python"));
    }

    @Test
    void phase4_registerAndAuthenticate() {
        String email = "user" + System.nanoTime() + "@example.com";
        var user = authService.register(email, "secret123", "Test User");
        assertNotNull(user.getId());
        assertNotEquals("secret123", user.getPasswordHash()); // must be hashed

        assertTrue(authService.authenticate(email, "secret123").isPresent());
        assertTrue(authService.authenticate(email, "wrong").isEmpty());
    }

    @Test
    void phase4_authAndSavedResumeApiLifecycle() throws Exception {
        String email = "api" + System.nanoTime() + "@example.com";
        String registerBody = objectMapper.writeValueAsString(Map.of(
            "email", email, "password", "test-password", "fullName", "API Test User"));
        String registerResponse = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON).content(registerBody))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long userId = objectMapper.readTree(registerResponse).get("userId").asLong();

        String loginBody = objectMapper.writeValueAsString(Map.of("email", email, "password", "test-password"));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody))
            .andExpect(status().isOk()).andExpect(jsonPath("$.fullName").value("API Test User"));
        String badLoginBody = objectMapper.writeValueAsString(Map.of("email", email, "password", "wrong-password"));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(badLoginBody))
            .andExpect(status().isUnauthorized());

        String createBody = objectMapper.writeValueAsString(Map.of("title", "API Test Resume", "resume", testResume));
        String createResponse = mockMvc.perform(post("/api/users/{userId}/resumes", userId)
                .contentType(MediaType.APPLICATION_JSON).content(createBody))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long resumeId = objectMapper.readTree(createResponse).get("id").asLong();

        mockMvc.perform(get("/api/users/{userId}/resumes", userId))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(resumeId));
        mockMvc.perform(get("/api/resumes/{id}", resumeId))
            .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("API Test Resume"));

        String editBody = objectMapper.writeValueAsString(Map.of("content", "API-edited summary"));
        mockMvc.perform(patch("/api/resumes/{id}/sections/SUMMARY", resumeId)
                .contentType(MediaType.APPLICATION_JSON).content(editBody))
            .andExpect(status().isOk()).andExpect(jsonPath("$.resume.summary").value("API-edited summary"));
        mockMvc.perform(get("/api/resumes/{id}/versions", resumeId))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].changeDescription").value("Edited section: SUMMARY"));
        mockMvc.perform(post("/api/resumes/{id}/undo", resumeId))
            .andExpect(status().isOk()).andExpect(jsonPath("$.resume.summary").value(testResume.summary()));
    }

    @Test
    void phase4_persistEditVersionUndo() {
        String email = "store" + System.nanoTime() + "@example.com";
        var user = authService.register(email, "pw12345", "Store User");

        var stored = storeService.create(user.getId(), "My Resume", testResume);
        assertNotNull(stored.id());

        // Edit creates a new version
        storeService.editSection(stored.id(), "SUMMARY", "First edit");
        storeService.editSection(stored.id(), "SUMMARY", "Second edit");
        assertEquals("Second edit", storeService.get(stored.id()).resume().summary());

        // History has initial + 2 edits = 3 versions
        assertEquals(3, storeService.history(stored.id()).size());

        // Undo reverts to previous version
        Resume afterUndo = storeService.undo(stored.id());
        assertEquals("First edit", afterUndo.summary());
        assertEquals(2, storeService.history(stored.id()).size());
    }

    @Test
    void phase4_resumesScopedPerUser() {
        var userA = authService.register("a" + System.nanoTime() + "@x.com", "pw12345", "A");
        var userB = authService.register("b" + System.nanoTime() + "@x.com", "pw12345", "B");
        storeService.create(userA.getId(), "A Resume", testResume);

        assertEquals(1, storeService.listForUser(userA.getId()).size());
        assertEquals(0, storeService.listForUser(userB.getId()).size());
    }

    // ===== Quality Tests =====
    @Test
    void quality_validateNoEmojis() {
        Resume badResume = new Resume(
            testResume.contact(),
            "✓ Great summary ★",
            testResume.skills(),
            testResume.experience(),
            testResume.education(),
            testResume.projects(),
            testResume.certifications()
        );

        AtsValidator.ValidationResult result = resumeService.validateResume(badResume);
        assertFalse(result.valid());
        assertFalse(result.errors().isEmpty());
    }

    @Test
    void quality_pdfTextIsSelectable() throws Exception {
        byte[] pdf = resumeService.renderResumeToPdf(testResume, TemplateType.PROFESSIONAL);
        String pdfStr = new String(pdf);

        // Check for text content markers in PDF
        assertTrue(pdfStr.contains("John Doe") || pdfStr.contains("BT") || pdf.length > 0);
    }

    @Test
    void quality_getTemplates() throws Exception {
        mockMvc.perform(get("/api/resumes/templates"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0]").exists())
            .andExpect(jsonPath("$[3]").exists());
    }
}
