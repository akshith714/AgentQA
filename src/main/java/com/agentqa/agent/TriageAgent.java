package com.agentqa.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.agentqa.llm.LlmClient;
import tools.jackson.databind.JsonNode;

@Component
public class TriageAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(TriageAgent.class);

    private static final String TEST_ERROR = "TEST_ERROR";
    private static final String CODE_DEFECT = "CODE_DEFECT";

    private final LlmClient llm;

    public TriageAgent(LlmClient llm) {
        this.llm = llm;
    }

    @Override
    public Agent.Node node() {
        return Agent.Node.TRIAGE;
    }

    @Override
    public void execute(Agent.State state) throws Exception {
        String system = """
                You are the Triage engineer on an automated QA team. A generated test suite failed.
                Decide the cause and reply ONLY with JSON, no markdown fences:
                {"verdict":"TEST_ERROR|CODE_DEFECT","finding":"one or two sentences","fixLine":<line number or 0>,"fixedCode":"the corrected line(s) only"}

                TEST_ERROR means the generated test itself is at fault: it does not compile, calls
                methods that do not exist, sets up data wrongly, or asserts something the merge
                request never promised. For TEST_ERROR set fixLine to 0 and fixedCode to "".

                CODE_DEFECT means the test is correct and the source code does not deliver the
                behaviour the merge request promised. State expected and actual values in the
                finding, set fixLine to the 1-based line number that must change, and set fixedCode
                to exactly what that line should say, with its original indentation. Do not include
                line numbers or diff markers in fixedCode. fixLine always refers to the FIRST source
                file listed below, because that is the only file the automated fix can change. If
                the defect is in any other file, describe it in the finding and set fixLine to 0.

                If the source under test failed to COMPILE, say so plainly in the finding and do not
                invent expected or actual test values, because no test was executed.
                """;

        StringBuilder sources = new StringBuilder();
        for (Agent.State.SourceFile f : state.getSources()) {
            sources.append("--- ").append(f.path()).append(" ---\n")
                   .append(f.code()).append("\n\n");
        }

        StringBuilder tests = new StringBuilder();
        for (String code : state.getGeneratedFiles()) {
            tests.append("--- ").append(RunnerAgent.className(code)).append(".java ---\n")
                 .append(code).append("\n\n");
        }

        String user = """
                Merge request intent: %s
                %s

                Sources under test (the first one is the only file the fix can change):
                %s

                Generated test code:
                %s

                Build and test output:
                %s
                """.formatted(state.getMrTitle(),
                              state.getMrDescription(),
                              sources,
                              tests,
                              state.getLastBuildError());

        log.info("[Triage] classifying the failure (run {})", state.getRunId());
        String reply = llm.complete(system, user);

        JsonNode parsed = llm.parse(reply);
        String verdict = parsed == null ? "" : parsed.path("verdict").asString("");
        if (parsed == null && reply != null && reply.contains("\"verdict\":\"CODE_DEFECT\"")) {
            // model didn't return clean JSON — fall back to the exact JSON fragment,
            // so prose such as "this is NOT a CODE_DEFECT" cannot trip the check
            verdict = CODE_DEFECT;
            log.warn("[Triage] reply was not valid JSON, used fallback parsing");
        }
        if (!TEST_ERROR.equals(verdict) && !CODE_DEFECT.equals(verdict)) {
            log.warn("[Triage] unusable verdict \"{}\" — defaulting to {} so the writer retries",
                    verdict, TEST_ERROR);
            verdict = TEST_ERROR;
        }
        String finding = parsed == null ? reply : parsed.path("finding").asString(reply);

        state.setVerdict(verdict);
        state.setFinding(finding);
        state.setFixLine(parsed == null ? 0 : parsed.path("fixLine").asInt(0));
        state.setFixedCode(parsed == null ? "" : parsed.path("fixedCode").asString(""));

        log.info("[Triage] verdict {} — {} (fix at line {})",
                verdict, finding, state.getFixLine());
    }
}
