package com.agentqa.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** Two generated classes with the same name must not overwrite each other. */
class TestClassNamingTest {

    @Test
    void keepsTheFirstNameWhenItIsFree() {
        Set<String> used = new HashSet<>();
        assertThat(RunnerAgent.uniqueName(used, "com.axelor", "LakeServiceTest"))
                .isEqualTo("LakeServiceTest");
    }

    @Test
    void suffixesARepeatedNameInTheSamePackage() {
        Set<String> used = new HashSet<>();
        RunnerAgent.uniqueName(used, "com.axelor", "GeneratedTest");
        assertThat(RunnerAgent.uniqueName(used, "com.axelor", "GeneratedTest"))
                .isEqualTo("GeneratedTest2");
        assertThat(RunnerAgent.uniqueName(used, "com.axelor", "GeneratedTest"))
                .isEqualTo("GeneratedTest3");
    }

    @Test
    void theSameNameInADifferentPackageIsNotAClash() {
        Set<String> used = new HashSet<>();
        RunnerAgent.uniqueName(used, "com.axelor.a", "GeneratedTest");
        assertThat(RunnerAgent.uniqueName(used, "com.axelor.b", "GeneratedTest"))
                .isEqualTo("GeneratedTest");
    }

    @Test
    void renamingRewritesTheDeclarationSoTheFileNameStaysLegal() {
        String code = """
                package com.axelor;

                class GeneratedTest {
                    void aGeneratedTestHelper() { }
                }
                """;
        String renamed = RunnerAgent.renameClass(code, "GeneratedTest", "GeneratedTest2");

        assertThat(renamed).contains("class GeneratedTest2 {");
        assertThat(renamed).contains("aGeneratedTestHelper");   // only the declaration changes
    }

    @Test
    void renamingIsANoOpWhenTheNameIsUnchanged() {
        String code = "class Same { }";
        assertThat(RunnerAgent.renameClass(code, "Same", "Same")).isEqualTo(code);
    }
}
