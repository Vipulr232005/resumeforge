package com.resumeforge.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "resume_versions")
public class ResumeVersion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "resume_id", nullable = false)
    private ResumeEntity resume;

    @Column(columnDefinition = "LONGTEXT", nullable = false)
    private String resumeJson;

    @Column
    private String changeDescription;

    @Column(nullable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    // Constructors
    public ResumeVersion() {}

    public ResumeVersion(ResumeEntity resume, String resumeJson, String changeDescription) {
        this.resume = resume;
        this.resumeJson = resumeJson;
        this.changeDescription = changeDescription;
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public ResumeEntity getResume() { return resume; }
    public void setResume(ResumeEntity resume) { this.resume = resume; }

    public String getResumeJson() { return resumeJson; }
    public void setResumeJson(String resumeJson) { this.resumeJson = resumeJson; }

    public String getChangeDescription() { return changeDescription; }
    public void setChangeDescription(String changeDescription) { this.changeDescription = changeDescription; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
