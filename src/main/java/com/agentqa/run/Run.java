package com.agentqa.run;

import java.time.Instant;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "runs")
public class Run {

    public enum Status {
        QUEUED,
        RUNNING,
        DONE,
        FAILED
    }

    /**
     * A commit in any of these states has already been handled. FAILED is included on
     * purpose: a rerun of the same sha would fail the same way.
     */
    public static final List<Status> ALREADY_SEEN =
            List.of(Status.QUEUED, Status.RUNNING, Status.DONE, Status.FAILED);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false)
    private Long mrIid;

    private String sourceBranch;

    private String headSha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.QUEUED;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant updatedAt;

    protected Run() { }

    public Run(Long projectId, Long mrIid, String sourceBranch, String headSha) {
        this.projectId = projectId;
        this.mrIid = mrIid;
        this.sourceBranch = sourceBranch;
        this.headSha = headSha;
    }

    public Long getId() { return id; }
    public Long getProjectId() { return projectId; }
    public Long getMrIid() { return mrIid; }
    public String getSourceBranch() { return sourceBranch; }
    public String getHeadSha() { return headSha; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setStatus(Status status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }
}
