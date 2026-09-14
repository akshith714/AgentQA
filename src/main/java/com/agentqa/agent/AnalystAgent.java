package com.agentqa.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.agentqa.gitlab.GitLabClient;
import com.agentqa.llm.LlmClient;
import tools.jackson.databind.JsonNode;

@Component
public class AnalystAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(AnalystAgent.class);

    private final LlmClient llm;
    private final GitLabClient gitlab;

    public AnalystAgent(LlmClient llm, GitLabClient gitlab) {
        this.llm = llm;
        this.gitlab = gitlab;
    }

    @Override
    public Agent.Node node() {
        return Agent.Node.ANALYST;
    }

    @Override
    public void execute(Agent.State state) throws Exception {
        JsonNode mr = gitlab.getMergeRequest(state.getProjectId(), state.getMrIid());
        String title = mr.path("title").asString("");
        String description = mr.path("description").asString("");

        JsonNode changes = gitlab.getChanges(state.getProjectId(), state.getMrIid());
        for (JsonNode change : changes.path("changes")) {
            String p = change.path("new_path").asString("");
            if (!p.endsWith(".java")) continue;
            if (state.getSources().size() >= 3) break;
            String src = gitlab.getFile(state.getProjectId(), p, state.getSourceBranch());
            state.getSources().add(new Agent.State.SourceFile(
                    p, p.substring(p.lastIndexOf('/') + 1), packageOf(src), src));
        }
        if (state.getSources().isEmpty()) {
            throw new IllegalStateException("No Java file changed in MR !" + state.getMrIid());
        }

        // the first file is what the Fixer will target if there's a defect
        Agent.State.SourceFile first = state.getSources().get(0);
        state.setSourceFilePath(first.path());
        state.setSourceFileName(first.fileName());
        state.setPackageName(first.packageName());
        state.setSourceCode(first.code());
        state.setMrTitle(title);
        state.setMrDescription(description);

        StringBuilder allSource = new StringBuilder();
        for (Agent.State.SourceFile f : state.getSources()) {
            allSource.append("--- ").append(f.path()).append(" ---\n")
                     .append(f.code()).append("\n\n");
        }

        log.info("[Analyst] MR !{}: \"{}\" — analysing {} file(s)",
                state.getMrIid(), title, state.getSources().size());

        String system = """
                You are the Analyst on an automated QA team reviewing a merge request.
                Reply ONLY with JSON, no markdown fences, in this exact shape:
                {"risk":"LOW|MEDIUM|HIGH","summary":"one sentence","testTargets":[{"className":"...","behaviorsToTest":["..."]}]}
                Only list behaviours that can be verified through the class's public API.
                Judge the code against what the merge request promises, not against itself.
                """;

        String user = """
                Merge request title: %s
                Merge request description: %s

                Changed source files:
                %s

                Produce one test plan covering these files. Use the exact class names shown.
                """.formatted(title, description, allSource);

        String plan = llm.complete(system, user);
        state.setTestPlan(plan);
        log.info("[Analyst] plan: {}", plan);
    }

    /** Reads the package declaration from Java source, or "" for the default package. */
    static String packageOf(String source) {
        for (String line : source.split("\n")) {
            String t = line.trim();
            if (t.startsWith("package ")) {
                return t.substring(8).replace(";", "").trim();
            }
            if (t.startsWith("import ") || t.startsWith("public ") || t.startsWith("class ")) {
                break;   // past the header, no package declared
            }
        }
        return "";
    }
}
