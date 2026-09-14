package com.agentqa.agent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RunnerAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(RunnerAgent.class);

    private static final int BUILD_TIMEOUT_MINUTES = 5;

    private static final String POM = """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion>
              <groupId>agentqa</groupId>
              <artifactId>sandbox</artifactId>
              <version>1.0</version>
              <properties>
                <maven.compiler.source>21</maven.compiler.source>
                <maven.compiler.target>21</maven.compiler.target>
                <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
              </properties>
              <dependencies>
                <dependency>
                  <groupId>org.junit.jupiter</groupId>
                  <artifactId>junit-jupiter</artifactId>
                  <version>5.10.2</version>
                  <scope>test</scope>
                </dependency>
              </dependencies>
              <build>
                <plugins>
                  <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-surefire-plugin</artifactId>
                    <version>3.2.5</version>
                  </plugin>
                </plugins>
              </build>
            </project>
            """;

    @Override
    public Agent.Node node() {
        return Agent.Node.RUNNER;
    }

    @Override
    public void execute(Agent.State state) throws Exception {
        build(state, state.getSourceCode());
    }

    public void build(Agent.State state, String sourceCode) throws Exception {
        Path work = Files.createTempDirectory("agentqa-run-" + state.getRunId() + "-");
        try {
            buildIn(work, state, sourceCode);
        } finally {
            try {
                deleteRecursively(work);
            } catch (IOException e) {
                log.warn("[Runner] could not delete sandbox {}: {}", work, e.getMessage());
            }
        }
    }

    private void buildIn(Path work, Agent.State state, String sourceCode) throws Exception {
        Files.writeString(work.resolve("pom.xml"), POM);

        for (Agent.State.SourceFile f : state.getSources()) {
            String pkgPath = f.packageName().isBlank() ? "" : f.packageName().replace('.', '/') + "/";
            Path dir = work.resolve("src/main/java/" + pkgPath);
            Files.createDirectories(dir);
            // the Fixer's corrected version replaces the first file only
            String content = (f == state.getSources().get(0)) ? sourceCode : f.code();
            Files.writeString(dir.resolve(f.fileName()), content);
        }

        Set<String> usedNames = new HashSet<>();
        for (String code : state.getGeneratedFiles()) {
            String pkg = packageOf(code);
            String pkgPath = pkg.isBlank() ? "" : pkg.replace('.', '/') + "/";
            Path dir = work.resolve("src/test/java/" + pkgPath);
            Files.createDirectories(dir);

            // two models can pick the same class name; renaming keeps both tests instead
            // of letting the second file silently overwrite the first
            String name = className(code);
            String unique = uniqueName(usedNames, pkg, name);
            Files.writeString(dir.resolve(unique + ".java"), renameClass(code, name, unique));
        }

        log.info("[Runner] building {} source(s) and {} test(s) in sandbox {}",
                state.getSources().size(), state.getGeneratedFiles().size(), work);

        Path cache = Path.of(System.getProperty("user.home"), ".m2");
        Files.createDirectories(cache);

        String container = "agentqa-build-" + state.getRunId() + "-" + System.nanoTime();
        Path logFile = Files.createTempFile("agentqa-build-", ".log");

        ProcessBuilder pb = new ProcessBuilder(
                "docker", "run", "--rm", "--name", container,
                "-v", work.toAbsolutePath() + ":/app",
                "-v", cache.toAbsolutePath() + ":/root/.m2",
                "-w", "/app",
                "maven:3.9-eclipse-temurin-21",
                "mvn", "-q", "-B", "test");
        pb.redirectErrorStream(true);
        // the output must go to a file, not a pipe: reading a pipe to EOF blocks until the
        // process exits, which would make the timeout below unreachable
        pb.redirectOutput(logFile.toFile());

        Process process = pb.start();
        boolean finished = process.waitFor(BUILD_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        if (!finished) {
            log.warn("[Runner] build exceeded {} minutes, killing container {}",
                    BUILD_TIMEOUT_MINUTES, container);
            killContainer(container);
            process.destroyForcibly();
            process.waitFor(30, TimeUnit.SECONDS);
        }
        int exit = finished ? process.exitValue() : -1;

        String output = readAndDelete(logFile);
        if (!finished) {
            output = output + "\n[AgentQA] the build was killed after "
                    + BUILD_TIMEOUT_MINUTES + " minutes without finishing.\n";
        }

        if (exit == 0) {
            state.setBuildPassed(true);
            state.setLastBuildError(null);
            log.info("[Runner] tests PASSED");
        } else {
            state.setBuildPassed(false);
            state.setLastBuildError(trim(output));
            log.warn("[Runner] tests FAILED (exit {}):\n{}", exit, trim(output));
        }
    }

    /** Removes the named container so a killed build leaves nothing running. */
    private void killContainer(String container) {
        try {
            new ProcessBuilder("docker", "rm", "-f", container)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start()
                    .waitFor(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            log.warn("[Runner] could not remove container {}: {}", container, e.getMessage());
        }
    }

    private String readAndDelete(Path logFile) {
        try {
            return new String(Files.readAllBytes(logFile), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("[Runner] could not read the build log: {}", e.getMessage());
            return "";
        } finally {
            try {
                Files.deleteIfExists(logFile);
            } catch (IOException e) {
                log.warn("[Runner] could not delete the build log {}: {}", logFile, e.getMessage());
            }
        }
    }

    /** Returns a class name not yet used in that package, recording it as taken. */
    static String uniqueName(Set<String> used, String pkg, String name) {
        String unique = name;
        for (int n = 2; !used.add(pkg + "." + unique); n++) {
            unique = name + n;
        }
        return unique;
    }

    /** Renames the test class declaration so its file name stays legal Java. */
    static String renameClass(String code, String from, String to) {
        if (from.equals(to)) {
            return code;
        }
        return Pattern.compile("\\bclass\\s+" + Pattern.quote(from) + "\\b")
                .matcher(code)
                .replaceFirst(Matcher.quoteReplacement("class " + to));
    }

    /** Reads the package declaration from Java source, or "" for the default package. */
    static String packageOf(String code) {
        for (String line : code.split("\n")) {
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

    static String className(String code) {
        for (String line : code.split("\n")) {
            String t = line.trim();
            if (t.startsWith("class ") || t.startsWith("public class ")) {
                String after = t.substring(t.indexOf("class ") + 6).trim();
                int end = after.indexOf(' ');
                return end > 0 ? after.substring(0, end) : after.replace("{", "").trim();
            }
        }
        return "GeneratedTest";
    }

    private String trim(String output) {
        String[] lines = output.split("\n");
        if (lines.length <= 60) {
            return output;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = lines.length - 60; i < lines.length; i++) {
            sb.append(lines[i]).append("\n");
        }
        return sb.toString();
    }

    private void deleteRecursively(Path path) throws IOException {
        try (var walk = Files.walk(path)) {
            walk.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                .forEach(p -> p.toFile().delete());
        }
    }
}
