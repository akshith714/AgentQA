package com.agentqa.worker;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.agentqa.agent.Agent;
import com.agentqa.graph.GraphRunner;
import com.agentqa.run.Run;
import com.agentqa.run.RunRepository;
import com.agentqa.run.StepRecordRepository;

@Component
public class RunWorker {

    private static final Logger log = LoggerFactory.getLogger(RunWorker.class);

    private static final int RETENTION_DAYS = 30;

    private final RunRepository runRepository;
    private final StepRecordRepository stepRepository;
    private final GraphRunner graphRunner;

    public RunWorker(RunRepository runRepository, StepRecordRepository stepRepository,
                     GraphRunner graphRunner) {
        this.runRepository = runRepository;
        this.stepRepository = stepRepository;
        this.graphRunner = graphRunner;
    }

    /**
     * Runs during bean initialisation, before the scheduler starts. An
     * ApplicationReadyEvent listener would race pollQueue and could re-queue a live run.
     */
    @jakarta.annotation.PostConstruct
    public void requeueOrphans() {
        List<Run> stuck = runRepository.findByStatusOrderByCreatedAtAsc(Run.Status.RUNNING);
        for (Run run : stuck) {
            run.setStatus(Run.Status.QUEUED);
            runRepository.save(run);
            log.warn("Re-queued orphaned run {} left RUNNING by a previous shutdown", run.getId());
        }
    }

    @Scheduled(fixedDelay = 3000)
    public void pollQueue() {
        List<Run> queued = runRepository.findByStatusOrderByCreatedAtAsc(Run.Status.QUEUED);
        for (Run run : queued) {
            run.setStatus(Run.Status.RUNNING);
            runRepository.save(run);
            log.info("Worker picked up run {}", run.getId());
            try {
                Agent.State state = new Agent.State(run.getId(), run.getProjectId(),
                        run.getMrIid(), run.getSourceBranch(), run.getHeadSha());
                graphRunner.run(state);
                run.setStatus(Run.Status.DONE);
            } catch (Exception e) {
                log.error("Run {} failed", run.getId(), e);
                run.setStatus(Run.Status.FAILED);
            }
            runRepository.save(run);
        }
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void purgeOldRuns() {
        Instant cutoff = Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS);
        List<Run> expired = runRepository.findByCreatedAtBefore(cutoff);
        if (expired.isEmpty()) {
            return;
        }
        stepRepository.deleteByRunIdIn(expired.stream().map(Run::getId).toList());
        runRepository.deleteAll(expired);
        log.info("Purged {} runs (and their steps) created before {}", expired.size(), cutoff);
    }
}
