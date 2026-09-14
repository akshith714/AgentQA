package com.agentqa.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.agentqa.gitlab.GitLabClient;
import com.agentqa.llm.LlmClient;

@Component
public class ReporterAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(ReporterAgent.class);

    private final GitLabClient gitlab;
    private final LlmClient llm;

    public ReporterAgent(GitLabClient gitlab, LlmClient llm) {
        this.gitlab = gitlab;
        this.llm = llm;
    }

    @Override
    public Agent.Node node() {
        return Agent.Node.REPORTER;
    }

    @Override
    public void execute(Agent.State state) throws Exception {
        StringBuilder body = new StringBuilder();
        body.append("### AgentQA · run #").append(state.getRunId()).append("\n\n");

        if (state.isBuildPassed()) {
            body.append("**All generated tests passed** after ")
                .append(state.getWriterAttempts()).append(" attempt(s).\n\n");
        } else if ("CODE_DEFECT".equals(state.getVerdict())) {
            body.append("**Possible defect found**\n\n")
                .append(state.getFinding() == null ? "(none)" : state.getFinding())
                .append("\n\n");
            if (state.getFixLine() > 0 && !state.getFixedCode().isBlank()) {
                body.append("Suggested fix for line ").append(state.getFixLine()).append(":\n\n")
                    .append("```suggestion\n")
                    .append(state.getFixedCode())
                    .append("\n```\n\n");
            }
            if (state.getFixMrIid() != null) {
                body.append("A verified fix is proposed in !").append(state.getFixMrIid())
                    .append(" — the generated tests pass against it.\n\n");
            }
        } else {
            body.append("**Could not produce passing tests** after ")
                .append(state.getWriterAttempts())
                .append(" attempts. A human should take a look.\n\n");
        }

        body.append("**Analysis**\n\n")
            .append(llm.field(state.getTestPlan(), "summary", "(none)")).append("\n\n");

        body.append("**Generated tests**\n\n");
        if (state.getGeneratedFiles().isEmpty()) {
            body.append("(none)\n");
        }
        for (String code : state.getGeneratedFiles()) {
            body.append("```java\n").append(code).append("\n```\n\n");
        }

        gitlab.postComment(state.getProjectId(), state.getMrIid(), body.toString());
        log.info("[Reporter] posted comment on MR !{}", state.getMrIid());
    }
}
