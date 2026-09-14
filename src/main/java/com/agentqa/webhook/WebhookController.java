package com.agentqa.webhook;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.agentqa.run.Run;
import com.agentqa.run.RunRepository;
import com.agentqa.security.ApiAuthFilter;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/webhook")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final RunRepository runRepository;
    private final String secret;

    public WebhookController(RunRepository runRepository,
                             @Value("${agentqa.webhook.secret}") String secret) {
        this.runRepository = runRepository;
        this.secret = secret;
    }

    @PostMapping("/gitlab")
    public ResponseEntity<String> gitlab(
            @RequestHeader(value = "X-Gitlab-Token", required = false) String token,
            @RequestBody JsonNode payload) {

        if (token == null || !ApiAuthFilter.constantTimeEquals(token, secret)) {
            log.warn("Webhook rejected: bad or missing X-Gitlab-Token");
            return ResponseEntity.status(401).body("invalid token");
        }

        String eventType = payload.path("object_kind").asString("");
        if (!"merge_request".equals(eventType)) {
            return ResponseEntity.ok("ignored: " + eventType);
        }

        JsonNode attrs = payload.path("object_attributes");
        String action = attrs.path("action").asString("");
        if (!List.of("open", "update", "reopen").contains(action)) {
            return ResponseEntity.ok("ignored action: " + action);
        }

        Long projectId = payload.path("project").path("id").asLong();
        Long mrIid = attrs.path("iid").asLong();
        String sourceBranch = attrs.path("source_branch").asString(null);
        String headSha = attrs.path("last_commit").path("id").asString(null);

        if (sourceBranch != null && sourceBranch.startsWith("agentqa/")) {
            log.info("Ignoring AgentQA's own branch {}", sourceBranch);
            return ResponseEntity.ok("ignored: agentqa branch");
        }

        boolean duplicate = runRepository.existsByProjectIdAndMrIidAndHeadShaAndStatusIn(
                projectId, mrIid, headSha, Run.ALREADY_SEEN);
        if (duplicate) {
            return ResponseEntity.ok("duplicate ignored");
        }

        Run run = runRepository.save(new Run(projectId, mrIid, sourceBranch, headSha));
        log.info("Queued run {} for project {} MR !{} sha {}", run.getId(), projectId, mrIid, headSha);
        return ResponseEntity.ok("queued run " + run.getId());
    }
}
