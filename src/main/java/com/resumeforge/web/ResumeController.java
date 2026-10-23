package com.resumeforge.web;

import com.resumeforge.model.Resume;
import com.resumeforge.service.ResumeService;
import com.resumeforge.service.ParserService;
import com.resumeforge.service.TailorService;
import com.resumeforge.template.TemplateType;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/resumes")
public class ResumeController {
    private final ResumeService resumeService;
    private final ParserService parserService;
    private final TailorService tailorService;

    public ResumeController(ResumeService resumeService, ParserService parserService, TailorService tailorService) {
        this.resumeService = resumeService;
        this.parserService = parserService;
        this.tailorService = tailorService;
    }

    @GetMapping("/templates")
    public TemplateType[] getAvailableTemplates() {
        return resumeService.getAvailableTemplates();
    }

    @PostMapping("/render")
    public ResponseEntity<byte[]> renderResume(
            @RequestBody Resume resume,
            @RequestParam(defaultValue = "MINIMAL") TemplateType template,
            @RequestParam(required = false) String accent) {
        try {
            byte[] pdf = resumeService.renderResumeToPdf(resume, template, accent);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDisposition(
                ContentDisposition.builder("attachment")
                    .filename("resume.pdf", StandardCharsets.UTF_8)
                    .build()
            );
            headers.setContentLength(pdf.length);

            return ResponseEntity.ok()
                .headers(headers)
                .body(pdf);
        } catch (IOException e) {
            throw new RuntimeException("Failed to render PDF: " + e.getMessage(), e);
        }
    }

    @PostMapping("/validate")
    public ValidationResponse validateResume(@RequestBody Resume resume) {
        var result = resumeService.validateResume(resume);
        return new ValidationResponse(result.valid(), result.errors(), result.warnings());
    }

    @PostMapping("/parse")
    public ResponseEntity<?> parseFile(@RequestParam("file") MultipartFile file) {
        try {
            ParserService.ParseResult result = parserService.parseFile(file);
            if (result.error() != null) {
                return ResponseEntity.badRequest().body(Map.of("error", result.error()));
            }
            Map<String, Object> resp = new HashMap<>();
            resp.put("resume", result.resume());
            resp.put("confidence", result.confidence());
            return ResponseEntity.ok(resp);
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to parse file: " + e.getMessage()));
        }
    }

    @PostMapping("/parse-text")
    public ResponseEntity<?> parseText(@RequestBody TextParseRequest request) {
        Resume resume = parserService.parseText(request.text());
        return ResponseEntity.ok(Map.of("resume", resume));
    }

    @PostMapping("/tailor")
    public ResponseEntity<?> tailorResume(@RequestBody TailorRequest request) {
        TailorService.TailoringResult result = tailorService.tailor(request.resume(), request.jd());
        Map<String, Object> resp = new HashMap<>();
        resp.put("resume", result.resume());
        resp.put("covered", result.covered());
        resp.put("missing", result.missing());
        resp.put("gaps", result.gaps());
        return ResponseEntity.ok(resp);
    }

    /** Paste raw resume text (e.g. copied from a PDF) + a JD -> parsed, JD-tailored resume. */
    @PostMapping("/smart")
    public ResponseEntity<?> smartBuild(@RequestBody SmartRequest request) {
        Resume parsed = parserService.parseText(request.rawText());
        TailorService.TailoringResult result = tailorService.tailor(parsed, request.jd());
        Map<String, Object> resp = new HashMap<>();
        resp.put("parsed", parsed);
        resp.put("resume", result.resume());
        resp.put("covered", result.covered());
        resp.put("missing", result.missing());
        resp.put("gaps", result.gaps());
        return ResponseEntity.ok(resp);
    }

    public record TailorRequest(Resume resume, String jd) {
    }

    public record SmartRequest(String rawText, String jd) {
    }

    public record ValidationResponse(
        boolean valid,
        java.util.List<String> errors,
        java.util.List<String> warnings
    ) {
    }

    public record TextParseRequest(String text) {
    }
}
