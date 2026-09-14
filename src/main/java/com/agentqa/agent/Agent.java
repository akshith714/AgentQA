package com.agentqa.agent;

import java.util.ArrayList;
import java.util.List;

public interface Agent {

    enum Node {
        ANALYST,
        TEST_WRITER,
        RUNNER,
        TRIAGE,
        FIXER,
        REPORTER,
        END
    }

    Node node();

    void execute(State state) throws Exception;

    class State {

        /** One changed source file from the merge request. */
        public record SourceFile(String path, String fileName, String packageName, String code) { }

        private final java.util.List<SourceFile> sources = new java.util.ArrayList<>();

        public java.util.List<SourceFile> getSources() { return sources; }

        private final Long runId;
        private final Long projectId;
        private final Long mrIid;
        private final String sourceBranch;
        private final String headSha;

        private String testPlan;
        private String sourceFileName;
        private String sourceCode;
        private final List<String> generatedFiles = new ArrayList<>();
        private String lastBuildError;
        private int writerAttempts = 0;
        private boolean buildPassed = false;
        private String mrTitle;
        private String mrDescription;
        private String verdict;
        private String finding;
        private int fixLine;
        private String fixedCode = "";
        private String sourceFilePath;
        private String packageName = "";
        private Long fixMrIid;

        public State(Long runId, Long projectId, Long mrIid, String sourceBranch, String headSha) {
            this.runId = runId;
            this.projectId = projectId;
            this.mrIid = mrIid;
            this.sourceBranch = sourceBranch;
            this.headSha = headSha;
        }

        public Long getRunId() { return runId; }
        public Long getProjectId() { return projectId; }
        public Long getMrIid() { return mrIid; }
        public String getSourceBranch() { return sourceBranch; }
        public String getHeadSha() { return headSha; }

        public String getTestPlan() { return testPlan; }
        public void setTestPlan(String testPlan) { this.testPlan = testPlan; }

        public String getSourceFileName() { return sourceFileName; }
        public void setSourceFileName(String sourceFileName) { this.sourceFileName = sourceFileName; }

        public String getSourceCode() { return sourceCode; }
        public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }

        public List<String> getGeneratedFiles() { return generatedFiles; }

        public String getLastBuildError() { return lastBuildError; }
        public void setLastBuildError(String lastBuildError) { this.lastBuildError = lastBuildError; }

        public int getWriterAttempts() { return writerAttempts; }
        public void incrementWriterAttempts() { this.writerAttempts++; }

        public boolean isBuildPassed() { return buildPassed; }
        public void setBuildPassed(boolean buildPassed) { this.buildPassed = buildPassed; }

        public String getVerdict() { return verdict; }
        public void setVerdict(String verdict) { this.verdict = verdict; }

        public String getFinding() { return finding; }
        public void setFinding(String finding) { this.finding = finding; }

        public int getFixLine() { return fixLine; }
        public void setFixLine(int fixLine) { this.fixLine = fixLine; }

        public String getFixedCode() { return fixedCode; }
        public void setFixedCode(String fixedCode) { this.fixedCode = fixedCode == null ? "" : fixedCode; }

        public String getSourceFilePath() { return sourceFilePath; }
        public void setSourceFilePath(String sourceFilePath) { this.sourceFilePath = sourceFilePath; }

        public String getPackageName() { return packageName; }
        public void setPackageName(String packageName) { this.packageName = packageName == null ? "" : packageName; }

        public Long getFixMrIid() { return fixMrIid; }
        public void setFixMrIid(Long fixMrIid) { this.fixMrIid = fixMrIid; }

        public String getMrTitle() { return mrTitle; }
        public void setMrTitle(String mrTitle) { this.mrTitle = mrTitle; }

        public String getMrDescription() { return mrDescription; }
        public void setMrDescription(String mrDescription) { this.mrDescription = mrDescription; }
    }
}
