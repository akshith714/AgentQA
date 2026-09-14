package com.agentqa.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import com.agentqa.run.Run;
import com.agentqa.run.RunRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Which merge request events become runs, and which are dropped at the door. */
class WebhookControllerTest {

    private static final String SECRET = "dev-secret-123";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RunRepository runs;
    private WebhookController controller;

    @BeforeEach
    void setUp() {
        runs = mock(RunRepository.class);
        controller = new WebhookController(runs, SECRET);
        when(runs.save(any(Run.class))).thenAnswer(call -> call.getArgument(0));
    }

    private JsonNode payload(String branch, String action) {
        return MAPPER.readTree("""
                {
                  "object_kind": "merge_request",
                  "project": {"id": 7},
                  "object_attributes": {
                    "iid": 42,
                    "action": "%s",
                    "source_branch": "%s",
                    "last_commit": {"id": "abc123"}
                  }
                }
                """.formatted(action, branch));
    }

    @Test
    void rejectsABadToken() {
        ResponseEntity<String> response = controller.gitlab("wrong", payload("feature/x", "open"));

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        verify(runs, never()).save(any());
    }

    @Test
    void rejectsAMissingToken() {
        assertThat(controller.gitlab(null, payload("feature/x", "open")).getStatusCode().value())
                .isEqualTo(401);
    }

    @Test
    void ignoresTheBotsOwnFixBranch() {
        ResponseEntity<String> response =
                controller.gitlab(SECRET, payload("agentqa/fix-mr-42", "open"));

        assertThat(response.getBody()).isEqualTo("ignored: agentqa branch");
        verify(runs, never()).save(any());
    }

    @Test
    void ignoresAnActionThatIsNotACodeChange() {
        assertThat(controller.gitlab(SECRET, payload("feature/x", "close")).getBody())
                .isEqualTo("ignored action: close");
        verify(runs, never()).save(any());
    }

    @Test
    void ignoresAShaThatHasAlreadyBeenSeen() {
        when(runs.existsByProjectIdAndMrIidAndHeadShaAndStatusIn(anyLong(), anyLong(), anyString(), any()))
                .thenReturn(true);

        assertThat(controller.gitlab(SECRET, payload("feature/x", "open")).getBody())
                .isEqualTo("duplicate ignored");
        verify(runs, never()).save(any());
    }

    /** A branch pipeline fires before the merge request exists and sends an empty iid. */
    private JsonNode branchPipelinePayload(String rawIid, String rawProjectId) {
        return MAPPER.readTree("""
                {
                  "object_kind": "merge_request",
                  "project": {"id": %s},
                  "object_attributes": {
                    "iid": %s,
                    "action": "open",
                    "source_branch": "feature/x",
                    "last_commit": {"id": "abc123"}
                  }
                }
                """.formatted(rawProjectId, rawIid));
    }

    @Test
    void anEmptyIidIsIgnoredRatherThanThrowing() {
        ResponseEntity<String> response =
                controller.gitlab(SECRET, branchPipelinePayload("\"\"", "7"));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo("ignored: no MR");
        verify(runs, never()).save(any());
    }

    @Test
    void aMissingIidIsIgnored() {
        assertThat(controller.gitlab(SECRET, branchPipelinePayload("null", "7")).getBody())
                .isEqualTo("ignored: no MR");
        verify(runs, never()).save(any());
    }

    @Test
    void anEmptyProjectIdIsIgnored() {
        assertThat(controller.gitlab(SECRET, branchPipelinePayload("42", "\"\"")).getBody())
                .isEqualTo("ignored: no MR");
        verify(runs, never()).save(any());
    }

    @Test
    void queuesAGenuineChange() {
        ResponseEntity<String> response = controller.gitlab(SECRET, payload("feature/x", "open"));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(runs).save(any(Run.class));
    }
}
