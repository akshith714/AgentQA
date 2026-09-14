package com.agentqa.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.agentqa.gitlab.GitLabClient;
import com.agentqa.llm.LlmClient;

@Component
public class FixerAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(FixerAgent.class);

    private final LlmClient llm;
    private final GitLabClient gitlab;
    private final RunnerAgent runner;

    public FixerAgent(LlmClient llm, GitLabClient gitlab, RunnerAgent runner) {
        this.llm = llm;
        this.gitlab = gitlab;
        this.runner = runner;
    }

    @Override
    public Agent.Node node() {
        return Agent.Node.FIXER;
    }

    @Override
    public void execute(Agent.State state) throws Exception {
        String system = """
                You are the Fixer on an automated QA team. A defect was confirmed in the source code.
                Return the COMPLETE corrected source file including its package declaration if the
                original had one, and nothing else: no markdown fences, no commentary.

                Rules:
                - Change as little as possible. Fix only what makes the failing tests pass.
                - Do not modify or weaken the tests. You are not shown them to change them.
                - Keep the existing class name, method signatures and formatting style.
                """;

        String user = """
                Merge request intent: %s
                %s

                Confirmed defect: %s

                Current source of %s:
                %s

                Failing test output:
                %s

                Return the corrected full source file.
                """.formatted(state.getMrTitle(), state.getMrDescription(), state.getFinding(),
                              state.getSourceFileName(), state.getSourceCode(), state.getLastBuildError());

        log.info("[Fixer] generating a corrected source (run {})", state.getRunId());
        String fixed = LlmClient.stripFences(llm.complete(system, user));

        boolean defectBuildPassed = state.isBuildPassed();
        String defectBuildError = state.getLastBuildError();

        runner.build(state, fixed);
        boolean fixWorks = state.isBuildPassed();

        state.setBuildPassed(defectBuildPassed);
        state.setLastBuildError(defectBuildError);

        if (!fixWorks) {
            log.warn("[Fixer] proposed fix did not pass the tests — not committing");
            return;
        }

        log.info("[Fixer] fix verified — tests pass against the corrected source");

        String branch = "agentqa/fix-mr-" + state.getMrIid();
        String head = gitlab.getBranchSha(state.getProjectId(), state.getSourceBranch());
        try {
            gitlab.createBranch(state.getProjectId(), branch, head);
        } catch (Exception e) {
            log.info("[Fixer] reusing existing branch {} ({})", branch, e.getMessage());
        }
        gitlab.updateFile(state.getProjectId(), state.getSourceFilePath(), branch, fixed,
                "AgentQA: fix for !" + state.getMrIid());

        try {
            Long iid = gitlab.createMergeRequest(state.getProjectId(), branch, state.getSourceBranch(),
                    "AgentQA: fix for !" + state.getMrIid(),
                    "Automated fix proposed by AgentQA.\n\n**Defect:** " + state.getFinding()
                            + "\n\nThe generated tests pass against this change. Please review before merging.");
            state.setFixMrIid(iid);
            log.info("[Fixer] opened fix merge request !{}", iid);
        } catch (Exception e) {
            log.info("[Fixer] a fix merge request already exists for !{} ({})",
                    state.getMrIid(), e.getMessage());
        }
    }
}
