package com.agentqa.run;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StepRecordRepository extends JpaRepository<StepRecord, Long> {
    List<StepRecord> findByRunIdOrderByStartedAtAsc(Long runId);

    void deleteByRunIdIn(List<Long> runIds);
}
