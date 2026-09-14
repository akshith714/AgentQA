package com.agentqa.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Models wrap code in fences however firmly the prompt forbids it. */
class LlmClientTest {

    @Test
    void stripsAFencedJavaBlock() {
        String reply = """
                ```java
                class Example { }
                ```
                """;
        assertThat(LlmClient.stripFences(reply)).isEqualTo("class Example { }");
    }

    @Test
    void stripsAFenceWithNoLanguageTag() {
        assertThat(LlmClient.stripFences("```\nclass Example { }\n```"))
                .isEqualTo("class Example { }");
    }

    @Test
    void leavesUnfencedCodeAlone() {
        assertThat(LlmClient.stripFences("  class Example { }  ")).isEqualTo("class Example { }");
    }

    @Test
    void leavesAnInnerBacktickRunAlone() {
        String reply = "class Example {\n    String s = \"``\";\n}";
        assertThat(LlmClient.stripFences(reply)).isEqualTo(reply);
    }

    @Test
    void handlesNull() {
        assertThat(LlmClient.stripFences(null)).isEmpty();
    }
}
