package com.agentqa.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The string parsing that decides where files land in the sandbox. */
class SourceParsingTest {

    @Test
    void readsThePackageDeclaration() {
        String source = """
                package com.axelor.lake.service;

                public class LakeService { }
                """;
        assertThat(RunnerAgent.packageOf(source)).isEqualTo("com.axelor.lake.service");
        assertThat(AnalystAgent.packageOf(source)).isEqualTo("com.axelor.lake.service");
    }

    @Test
    void readsThePackageAfterALicenceHeader() {
        String source = """
                /*
                 * Copyright notice.
                 */

                package com.axelor.lake;

                public class LakeService { }
                """;
        assertThat(RunnerAgent.packageOf(source)).isEqualTo("com.axelor.lake");
    }

    @Test
    void returnsEmptyForTheDefaultPackage() {
        String source = """
                import java.util.List;

                public class Calculator { }
                """;
        assertThat(RunnerAgent.packageOf(source)).isEmpty();
        assertThat(AnalystAgent.packageOf(source)).isEmpty();
    }

    @Test
    void doesNotMistakeALaterWordForAPackage() {
        String source = """
                public class Doc {
                    // package private on purpose
                    void helper() { }
                }
                """;
        assertThat(RunnerAgent.packageOf(source)).isEmpty();
    }

    @Test
    void readsThePublicClassName() {
        assertThat(RunnerAgent.className("public class LakeServiceTest {")).isEqualTo("LakeServiceTest");
        assertThat(RunnerAgent.className("class Plain {")).isEqualTo("Plain");
    }

    @Test
    void fallsBackWhenNoClassIsDeclared() {
        assertThat(RunnerAgent.className("not java at all")).isEqualTo("GeneratedTest");
    }
}
