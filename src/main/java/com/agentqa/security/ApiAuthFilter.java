package com.agentqa.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Guards the read endpoints with HTTP Basic. They expose source code, findings and build
 * output, so they must not be open to anyone who can reach the port. The webhook is exempt
 * because GitLab cannot send Basic credentials and it already checks its own token.
 */
@Component
public class ApiAuthFilter extends OncePerRequestFilter {

    private static final String REALM = "AgentQA";

    private final String username;
    private final String password;

    public ApiAuthFilter(@Value("${agentqa.dashboard.username}") String username,
                         @Value("${agentqa.dashboard.password}") String password) {
        this.username = username;
        this.password = password;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/webhook");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (authorised(request.getHeader("Authorization"))) {
            chain.doFilter(request, response);
            return;
        }
        response.setHeader("WWW-Authenticate", "Basic realm=\"" + REALM + "\", charset=\"UTF-8\"");
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "authentication required");
    }

    private boolean authorised(String header) {
        if (header == null || !header.startsWith("Basic ")) {
            return false;
        }
        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(header.substring(6).trim()),
                    StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return false;
        }
        int separator = decoded.indexOf(':');
        if (separator < 0) {
            return false;
        }
        return constantTimeEquals(decoded.substring(0, separator), username)
                && constantTimeEquals(decoded.substring(separator + 1), password);
    }

    /** Compares without leaking the answer through how long the comparison takes. */
    public static boolean constantTimeEquals(String actual, String expected) {
        return MessageDigest.isEqual(
                actual.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));
    }
}
