package com.agentqa.gitlab;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;

@Component
public class GitLabClient {

    private final RestClient http;

    public GitLabClient(@Value("${agentqa.gitlab.base-url}") String baseUrl) {
        String token = System.getenv("GITLAB_TOKEN");
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("GITLAB_TOKEN environment variable is not set");
        }
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(30_000);

        this.http = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(baseUrl + "/api/v4")
                .defaultHeader("PRIVATE-TOKEN", token)
                .build();
    }

    public JsonNode getMergeRequest(Long projectId, Long mrIid) {
        return http.get()
                .uri("/projects/{p}/merge_requests/{i}", projectId, mrIid)
                .retrieve()
                .body(JsonNode.class);
    }

    public JsonNode getChanges(Long projectId, Long mrIid) {
        return http.get()
                .uri("/projects/{p}/merge_requests/{i}/changes", projectId, mrIid)
                .retrieve()
                .body(JsonNode.class);
    }

    public String getFile(Long projectId, String path, String ref) {
        String encoded = URLEncoder.encode(path, StandardCharsets.UTF_8);
        return http.get()
                .uri("/projects/{p}/repository/files/" + encoded + "/raw?ref={r}", projectId, ref)
                .retrieve()
                .body(String.class);
    }

    public void postComment(Long projectId, Long mrIid, String body) {
        http.post()
                .uri("/projects/{p}/merge_requests/{i}/notes", projectId, mrIid)
                .body(Map.of("body", body))
                .retrieve()
                .toBodilessEntity();
    }

    public String getBranchSha(Long projectId, String branch) {
        String encoded = URLEncoder.encode(branch, StandardCharsets.UTF_8);
        JsonNode node = http.get()
                .uri("/projects/{p}/repository/branches/" + encoded, projectId)
                .retrieve()
                .body(JsonNode.class);
        return node.path("commit").path("id").asString("");
    }

    public void createBranch(Long projectId, String newBranch, String fromRef) {
        http.post()
                .uri("/projects/{p}/repository/branches?branch={b}&ref={r}", projectId, newBranch, fromRef)
                .retrieve()
                .toBodilessEntity();
    }

    public void updateFile(Long projectId, String path, String branch, String content, String message) {
        String encoded = URLEncoder.encode(path, StandardCharsets.UTF_8);
        http.put()
                .uri("/projects/{p}/repository/files/" + encoded, projectId)
                .body(Map.of("branch", branch, "content", content, "commit_message", message))
                .retrieve()
                .toBodilessEntity();
    }

    public Long createMergeRequest(Long projectId, String source, String target,
                                   String title, String description) {
        JsonNode node = http.post()
                .uri("/projects/{p}/merge_requests", projectId)
                .body(Map.of("source_branch", source, "target_branch", target,
                             "title", title, "description", description))
                .retrieve()
                .body(JsonNode.class);
        return node.path("iid").asLong();
    }
}
