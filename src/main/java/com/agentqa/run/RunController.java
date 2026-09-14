package com.agentqa.run;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/runs")
public class RunController {

    private final RunRepository runRepository;
    private final StepRecordRepository stepRepository;

    public RunController(RunRepository runRepository, StepRecordRepository stepRepository) {
        this.runRepository = runRepository;
        this.stepRepository = stepRepository;
    }

    @GetMapping("/{id}")
    public ResponseEntity<RunDetail> get(@PathVariable Long id) {
        return runRepository.findById(id)
                .map(run -> ResponseEntity.ok(detail(run)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    public List<RunSummary> recent(@RequestParam(defaultValue = "20") int limit) {
        int capped = Math.clamp(limit, 1, 100);
        return runRepository.findAll(PageRequest.of(0, capped, Sort.by(Sort.Direction.DESC, "id")))
                .map(run -> new RunSummary(
                        run.getId(),
                        run.getMrIid(),
                        run.getStatus(),
                        run.getCreatedAt(),
                        seconds(run.getCreatedAt(), run.getUpdatedAt())))
                .getContent();
    }

    private RunDetail detail(Run run) {
        List<StepView> steps = stepRepository.findByRunIdOrderByStartedAtAsc(run.getId()).stream()
                .map(step -> new StepView(
                        step.getAgent(),
                        step.getAttempt(),
                        step.getOutcome(),
                        step.getStartedAt(),
                        step.getFinishedAt(),
                        seconds(step.getStartedAt(), step.getFinishedAt()),
                        step.getDetail()))
                .toList();

        String path = steps.stream()
                .map(step -> step.agent() + "#" + step.attempt())
                .collect(Collectors.joining(" -> "));

        return new RunDetail(
                run.getId(),
                run.getProjectId(),
                run.getMrIid(),
                run.getSourceBranch(),
                run.getHeadSha(),
                run.getStatus(),
                run.getCreatedAt(),
                run.getUpdatedAt(),
                seconds(run.getCreatedAt(), run.getUpdatedAt()),
                path,
                steps);
    }

    private static Double seconds(Instant from, Instant to) {
        if (from == null || to == null) {
            return null;
        }
        return Duration.between(from, to).toMillis() / 1000.0;
    }

    public record RunDetail(
            Long id,
            Long projectId,
            Long mrIid,
            String sourceBranch,
            String headSha,
            Run.Status status,
            Instant createdAt,
            Instant updatedAt,
            Double durationSeconds,
            String path,
            List<StepView> steps) { }

    public record StepView(
            String agent,
            int attempt,
            String outcome,
            Instant startedAt,
            Instant finishedAt,
            Double durationSeconds,
            String detail) { }

    public record RunSummary(
            Long id,
            Long mrIid,
            Run.Status status,
            Instant createdAt,
            Double durationSeconds) { }
}
