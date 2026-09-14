package com.agentqa.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RunRepositoryTest {

    private static final long PROJECT = 999_000_001L;
    private static final long MR = 4242L;
    private static final String SHA = "abc123def456";

    @Autowired
    private RunRepository runs;

    @Test
    void sameCommitIsADuplicateWhileQueued() {
        runs.save(new Run(PROJECT, MR, "feature/x", SHA));

        assertTrue(runs.existsByProjectIdAndMrIidAndHeadShaAndStatusIn(
                PROJECT, MR, SHA, Run.ALREADY_SEEN));
    }

    @Test
    void sameCommitIsADuplicateWhileRunning() {
        Run run = runs.save(new Run(PROJECT, MR, "feature/x", SHA));
        run.setStatus(Run.Status.RUNNING);
        runs.save(run);

        assertTrue(runs.existsByProjectIdAndMrIidAndHeadShaAndStatusIn(
                PROJECT, MR, SHA, Run.ALREADY_SEEN));
    }

    @Test
    void aNewCommitOnTheSameMergeRequestIsNotADuplicate() {
        runs.save(new Run(PROJECT, MR, "feature/x", SHA));

        assertFalse(runs.existsByProjectIdAndMrIidAndHeadShaAndStatusIn(
                PROJECT, MR, "a-different-sha", Run.ALREADY_SEEN));
    }

    @Test
    void aFinishedRunBlocksTheSameCommitFromRunningAgain() {
        Run run = runs.save(new Run(PROJECT, MR, "feature/x", SHA));
        run.setStatus(Run.Status.DONE);
        runs.save(run);

        assertTrue(runs.existsByProjectIdAndMrIidAndHeadShaAndStatusIn(
                PROJECT, MR, SHA, Run.ALREADY_SEEN));
    }

    @Test
    void aFailedRunAlsoBlocksTheSameCommit() {
        Run run = runs.save(new Run(PROJECT, MR, "feature/x", SHA));
        run.setStatus(Run.Status.FAILED);
        runs.save(run);

        assertTrue(runs.existsByProjectIdAndMrIidAndHeadShaAndStatusIn(
                PROJECT, MR, SHA, Run.ALREADY_SEEN));
    }

    @Test
    void onlyQueuedRunsArePickedUpByTheWorker() {
        Run queued = runs.save(new Run(PROJECT, MR, "feature/x", SHA));
        Run done = runs.save(new Run(PROJECT, MR + 1, "feature/y", "othersha"));
        done.setStatus(Run.Status.DONE);
        runs.save(done);

        List<Run> found = runs.findByStatusOrderByCreatedAtAsc(Run.Status.QUEUED).stream()
                .filter(r -> PROJECT == r.getProjectId())
                .toList();

        assertEquals(1, found.size());
        assertEquals(queued.getId(), found.get(0).getId());
    }
}
