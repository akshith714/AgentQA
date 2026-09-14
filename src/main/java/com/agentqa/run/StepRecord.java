package com.agentqa.run;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "steps")
public class StepRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long runId;

    @Column(nullable = false)
    private String agent;

    @Column(nullable = false)
    private int attempt;

    @Column(nullable = false)
    private Instant startedAt;

    private Instant finishedAt;

    private String outcome;

    private String detail;

    protected StepRecord() { }

    public StepRecord(Long runId, String agent, int attempt) {
        this.runId = runId;
        this.agent = agent;
        this.attempt = attempt;
        this.startedAt = Instant.now();
    }

    public void finish(String outcome, String detail) {
        this.finishedAt = Instant.now();
        this.outcome = outcome;
        this.detail = detail;
    }

    public Long getId() { return id; }
    public Long getRunId() { return runId; }
    public String getAgent() { return agent; }
    public int getAttempt() { return attempt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public String getOutcome() { return outcome; }
    public String getDetail() { return detail; }
}
