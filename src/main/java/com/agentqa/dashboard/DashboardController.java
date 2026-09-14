package com.agentqa.dashboard;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class DashboardController {

    private final List<SseEmitter> clients = new CopyOnWriteArrayList<>();

    @GetMapping("/dashboard/stream")
    public SseEmitter stream() {
        SseEmitter emitter = new SseEmitter(0L);
        clients.add(emitter);
        emitter.onCompletion(() -> clients.remove(emitter));
        emitter.onTimeout(() -> clients.remove(emitter));
        return emitter;
    }

    public void publish(String json) {
        for (SseEmitter client : clients) {
            try {
                client.send(SseEmitter.event().data(json));
            } catch (IOException | IllegalStateException e) {
                clients.remove(client);
                try {
                    client.complete();
                } catch (RuntimeException ignored) {
                    // the connection is already gone; nothing left to close
                }
            }
        }
    }
}
