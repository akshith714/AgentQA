package com.agentqa.graph;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.agentqa.agent.Agent;
import com.agentqa.dashboard.DashboardController;
import com.agentqa.llm.LlmClient;
import com.agentqa.run.StepRecord;
import com.agentqa.run.StepRecordRepository;
import tools.jackson.databind.ObjectMapper;

@Component
public class GraphRunner {

    private static final Logger log = LoggerFactory.getLogger(GraphRunner.class);

    private final Map<Agent.Node, Agent> agents;
    private final StepRecordRepository stepRepository;
    private final DashboardController dashboard;
    private final ObjectMapper mapper;
    private final LlmClient llm;

    public GraphRunner(List<Agent> agentList, StepRecordRepository stepRepository,
                       DashboardController dashboard, ObjectMapper mapper, LlmClient llm) {
        this.agents = agentList.stream()
                .collect(Collectors.toMap(Agent::node, Function.identity()));
        this.stepRepository = stepRepository;
        this.dashboard = dashboard;
        this.mapper = mapper;
        this.llm = llm;
    }

    public void run(Agent.State state) throws Exception {
        Agent.Node node = Agent.Node.ANALYST;
        while (node != Agent.Node.END) {
            Agent agent = agents.get(node);
            int attempt = attemptFor(node, state);
            StepRecord step = new StepRecord(state.getRunId(), node.name(), attempt);
            stepRepository.save(step);
            log.info("--- entering node {} (run {}, attempt {}) ---", node, state.getRunId(), attempt);
            publish(state, node.name(), attempt, "running", null, 0);
            long startedNanos = System.nanoTime();
            try {
                agent.execute(state);
                step.finish("OK", null);
                publish(state, node.name(), attempt, "ok", null, millisSince(startedNanos));
            } catch (Exception e) {
                step.finish("FAILED", e.getMessage());
                stepRepository.save(step);
                publish(state, node.name(), attempt, "failed", e.getMessage(), millisSince(startedNanos));
                throw e;
            }
            stepRepository.save(step);
            node = next(node, state);
        }
        log.info("--- graph finished (run {}) ---", state.getRunId());
    }

    private int attemptFor(Agent.Node node, Agent.State state) {
        return node == Agent.Node.TEST_WRITER
                ? state.getWriterAttempts() + 1
                : Math.max(state.getWriterAttempts(), 1);
    }

    private Agent.Node next(Agent.Node current, Agent.State state) {
        return switch (current) {
            case ANALYST -> Agent.Node.TEST_WRITER;
            case TEST_WRITER -> Agent.Node.RUNNER;
            case RUNNER -> state.isBuildPassed() ? Agent.Node.REPORTER : Agent.Node.TRIAGE;
            case TRIAGE -> {
                if ("TEST_ERROR".equals(state.getVerdict())) {
                    yield state.getWriterAttempts() < 3 ? Agent.Node.TEST_WRITER : Agent.Node.REPORTER;
                }
                yield Agent.Node.FIXER;
            }
            case FIXER -> Agent.Node.REPORTER;
            case REPORTER -> Agent.Node.END;
            case END -> Agent.Node.END;
        };
    }

    private void publish(Agent.State state, String agent, int attempt, String stateName,
                         String detail, long ms) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("runId", state.getRunId());
        event.put("mrIid", state.getMrIid());
        event.put("agent", agent);
        event.put("attempt", attempt);
        event.put("state", stateName);
        event.put("detail", detail == null ? "" : detail);
        event.put("ms", ms);
        event.put("risk", llm.field(state.getTestPlan(), "risk", ""));
        event.put("tests", countTests(state));
        event.put("build", state.isBuildPassed() ? "PASSED" : "");
        event.put("verdict", state.getVerdict() == null ? "" : state.getVerdict());
        event.put("finding", state.getFinding() == null ? "" : state.getFinding());
        event.put("fixMr", state.getFixMrIid());
        dashboard.publish(mapper.writeValueAsString(event));
    }

    private static long millisSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private static int countTests(Agent.State state) {
        int count = 0;
        for (String code : state.getGeneratedFiles()) {
            for (int i = code.indexOf("@Test"); i >= 0; i = code.indexOf("@Test", i + 5)) {
                count++;
            }
        }
        return count;
    }
}
