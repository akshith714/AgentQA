package com.agentqa.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.agentqa.llm.LlmClient;

@Component
public class TestWriterAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(TestWriterAgent.class);

    private final LlmClient llm;

    public TestWriterAgent(LlmClient llm) {
        this.llm = llm;
    }

    @Override
    public Agent.Node node() {
        return Agent.Node.TEST_WRITER;
    }

    @Override
    public void execute(Agent.State state) throws Exception {
        String system = """
                You are the Test Writer on an automated QA team.
                Write ONE JUnit 5 test class in Java for the class under test.

                Rules you must follow:
                - The class under test is already on the classpath. Use it directly.
                - Your test class must declare the SAME package as the class under test, if it has one.
                - Do NOT use reflection (no Class.forName, no Method.invoke).
                - Do NOT use Assumptions or any construct that skips tests.
                - Do NOT redefine or stub the class under test.
                - Only call methods that exist in the source shown to you.
                - If a behaviour cannot be tested through the public API, omit that test.
                - Use org.junit.jupiter.api.Test and org.junit.jupiter.api.Assertions.
                - Test the behaviour described in the test plan, not the behaviour you infer from the source.
                - If the source appears to contradict the plan, still assert what the plan says. A failing test that exposes a real defect is a valuable and correct outcome.
                - Never weaken an assertion to make a test pass.
                - Reply with ONLY the Java source code: no markdown fences, no commentary.
                """;

        state.incrementWriterAttempts();
        log.info("[Test writer] generating tests for {} file(s) (run {}, attempt {})",
                state.getSources().size(), state.getRunId(), state.getWriterAttempts());

        state.getGeneratedFiles().clear();
        for (Agent.State.SourceFile f : state.getSources()) {
            StringBuilder user = new StringBuilder();
            user.append("Source of the class under test (").append(f.path()).append("):\n\n")
                .append(f.code()).append("\n\n")
                .append("Test plan:\n").append(state.getTestPlan()).append("\n\n");
            if (state.getLastBuildError() != null) {
                user.append("Your previous attempt failed with this build output:\n")
                    .append(state.getLastBuildError())
                    .append("\nFix the test class. Do not work around the failure with reflection or skipped tests.\n");
            }
            String code = LlmClient.stripFences(llm.complete(system, user.toString()));
            state.getGeneratedFiles().add(code);
        }
        log.info("[Test writer] generated {} test class(es)", state.getGeneratedFiles().size());
    }
}
