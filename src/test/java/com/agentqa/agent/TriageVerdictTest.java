package com.agentqa.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.agentqa.llm.LlmClient;
import tools.jackson.databind.ObjectMapper;

/**
 * Triage decides whether the generated test or the source code is at fault, so an
 * unusable reply must never be read as a confirmed defect.
 */
class TriageVerdictTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private LlmClient llm;
    private TriageAgent triage;
    private Agent.State state;

    @BeforeEach
    void setUp() {
        llm = mock(LlmClient.class);
        triage = new TriageAgent(llm);
        state = new Agent.State(1L, 7L, 42L, "feature/x", "abc123");
        state.getGeneratedFiles().add("class ExampleTest { }");
    }

    private void modelReplies(String reply, boolean parseable) {
        when(llm.complete(anyString(), anyString())).thenReturn(reply);
        when(llm.parse(anyString())).thenReturn(parseable ? MAPPER.readTree(reply) : null);
    }

    @Test
    void readsACleanJsonVerdict() throws Exception {
        modelReplies("""
                {"verdict":"CODE_DEFECT","finding":"returns 0 for an empty lake",\
                "fixLine":12,"fixedCode":"    return total;"}""", true);

        triage.execute(state);

        assertThat(state.getVerdict()).isEqualTo("CODE_DEFECT");
        assertThat(state.getFinding()).isEqualTo("returns 0 for an empty lake");
        assertThat(state.getFixLine()).isEqualTo(12);
        assertThat(state.getFixedCode()).isEqualTo("    return total;");
    }

    @Test
    void fallsBackToTheJsonFragmentWhenParsingFails() throws Exception {
        modelReplies("Sorry! {\"verdict\":\"CODE_DEFECT\"} but the JSON is broken", false);

        triage.execute(state);

        assertThat(state.getVerdict()).isEqualTo("CODE_DEFECT");
    }

    @Test
    void proseMentioningTheWordIsNotAVerdict() throws Exception {
        modelReplies("This is NOT a CODE_DEFECT, the generated test is wrong.", false);

        triage.execute(state);

        assertThat(state.getVerdict()).isEqualTo("TEST_ERROR");
    }

    @Test
    void anUnusableVerdictDefaultsToTestErrorSoTheWriterRetries() throws Exception {
        modelReplies("{\"verdict\":\"MAYBE\",\"finding\":\"unsure\"}", true);

        triage.execute(state);

        assertThat(state.getVerdict()).isEqualTo("TEST_ERROR");
    }
}
