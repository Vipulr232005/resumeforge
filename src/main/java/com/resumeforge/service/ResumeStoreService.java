package com.resumeforge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeforge.model.Resume;
import com.resumeforge.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class ResumeStoreService {
    private final UserAccountRepository userRepository;
    private final ResumeEntityRepository resumeRepository;
    private final ResumeVersionRepository versionRepository;
    private final EditService editService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ResumeStoreService(UserAccountRepository userRepository,
                              ResumeEntityRepository resumeRepository,
                              ResumeVersionRepository versionRepository,
                              EditService editService) {
        this.userRepository = userRepository;
        this.resumeRepository = resumeRepository;
        this.versionRepository = versionRepository;
        this.editService = editService;
    }

    @Transactional
    public StoredResume create(Long userId, String title, Resume resume) {
        UserAccount user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        String json = toJson(resume);
        ResumeEntity entity = resumeRepository.save(new ResumeEntity(user, title, json));
        versionRepository.save(new ResumeVersion(entity, json, "Initial version"));
        return toStoredResume(entity);
    }

    public List<StoredResume> listForUser(Long userId) {
        return resumeRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
            .map(this::toStoredResume)
            .toList();
    }

    public StoredResume get(Long resumeId) {
        return toStoredResume(loadEntity(resumeId));
    }

    @Transactional
    public EditService.EditResult editSection(Long resumeId, String section, String newContent) {
        ResumeEntity entity = loadEntity(resumeId);
        Resume current = fromJson(entity.getResumeJson());

        EditService.EditResult result = editService.editSection(current, section, newContent);

        String updatedJson = toJson(result.resume());
        entity.setResumeJson(updatedJson);
        entity.setUpdatedAt(java.time.LocalDateTime.now());
        resumeRepository.save(entity);
        versionRepository.save(new ResumeVersion(entity, updatedJson, "Edited section: " + section));

        return result;
    }

    /**
     * Reverts the resume to its previous saved version and returns the restored resume.
     * The current state is dropped (its version row is removed) so repeated undo walks back the history.
     */
    @Transactional
    public Resume undo(Long resumeId) {
        ResumeEntity entity = loadEntity(resumeId);
        List<ResumeVersion> history = versionRepository.findByResumeIdOrderByCreatedAtDesc(resumeId);
        if (history.size() < 2) {
            throw new IllegalStateException("No previous version to undo to.");
        }
        ResumeVersion current = history.get(0);
        ResumeVersion previous = history.get(1);

        entity.setResumeJson(previous.getResumeJson());
        entity.setUpdatedAt(java.time.LocalDateTime.now());
        resumeRepository.save(entity);
        versionRepository.delete(current);

        return fromJson(previous.getResumeJson());
    }

    public List<VersionInfo> history(Long resumeId) {
        return versionRepository.findByResumeIdOrderByCreatedAtDesc(resumeId).stream()
            .map(v -> new VersionInfo(v.getId(), v.getChangeDescription(), v.getCreatedAt()))
            .toList();
    }

    private ResumeEntity loadEntity(Long resumeId) {
        return resumeRepository.findById(resumeId)
            .orElseThrow(() -> new IllegalArgumentException("Resume not found: " + resumeId));
    }

    private StoredResume toStoredResume(ResumeEntity entity) {
        return new StoredResume(
            entity.getId(),
            entity.getUser().getId(),
            entity.getTitle(),
            fromJson(entity.getResumeJson()),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    private String toJson(Resume resume) {
        try {
            return objectMapper.writeValueAsString(resume);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize resume", e);
        }
    }

    private Resume fromJson(String json) {
        try {
            return objectMapper.readValue(json, Resume.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize resume", e);
        }
    }

    public record StoredResume(
        Long id,
        Long userId,
        String title,
        Resume resume,
        java.time.LocalDateTime createdAt,
        java.time.LocalDateTime updatedAt
    ) {
    }

    public record VersionInfo(
        Long id,
        String changeDescription,
        java.time.LocalDateTime createdAt
    ) {
    }
}
