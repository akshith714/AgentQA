package com.agentqa.run;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RunRepository extends JpaRepository<Run, Long> {

    List<Run> findByStatusOrderByCreatedAtAsc(Run.Status status);

    List<Run> findByCreatedAtBefore(Instant cutoff);

    boolean existsByProjectIdAndMrIidAndHeadShaAndStatusIn(
            Long projectId, Long mrIid, String headSha, List<Run.Status> statuses);
}
