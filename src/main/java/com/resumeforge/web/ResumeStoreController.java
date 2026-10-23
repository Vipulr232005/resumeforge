package com.resumeforge.web;

import com.resumeforge.model.Resume;
import com.resumeforge.service.EditService;
import com.resumeforge.service.ResumeStoreService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ResumeStoreController {
    private final ResumeStoreService storeService;

    public ResumeStoreController(ResumeStoreService storeService) {
        this.storeService = storeService;
    }

    @PostMapping("/users/{userId}/resumes")
    public ResponseEntity<?> create(@PathVariable Long userId, @RequestBody CreateRequest request) {
        try {
            var stored = storeService.create(userId, request.title(), request.resume());
            return ResponseEntity.ok(stored);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/users/{userId}/resumes")
    public ResponseEntity<?> list(@PathVariable Long userId) {
        return ResponseEntity.ok(storeService.listForUser(userId));
    }

    @GetMapping("/resumes/{id}")
    public ResponseEntity<?> get(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(storeService.get(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/resumes/{id}/sections/{section}")
    public ResponseEntity<?> editSection(@PathVariable Long id,
                                         @PathVariable String section,
                                         @RequestBody EditRequest request) {
        try {
            EditService.EditResult result = storeService.editSection(id, section, request.content());
            return ResponseEntity.ok(Map.of(
                "resume", result.resume(),
                "diff", result.diff()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/resumes/{id}/undo")
    public ResponseEntity<?> undo(@PathVariable Long id) {
        try {
            Resume restored = storeService.undo(id);
            return ResponseEntity.ok(Map.of("resume", restored));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/resumes/{id}/versions")
    public ResponseEntity<?> versions(@PathVariable Long id) {
        return ResponseEntity.ok(storeService.history(id));
    }

    public record CreateRequest(String title, Resume resume) {
    }

    public record EditRequest(String content) {
    }
}
