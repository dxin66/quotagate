package com.quotagate.api;

import com.quotagate.api.dto.ChatRequest;
import com.quotagate.api.dto.ChatResponse;
import com.quotagate.route.ProviderRouter;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.beans.factory.annotation.Qualifier;

import java.io.IOException;
import java.util.concurrent.Executor;

@RestController
@RequestMapping("/v1")
public class ChatController {

    private final ProviderRouter providerRouter;
    private final Executor streamExecutor;

    public ChatController(
            ProviderRouter providerRouter,
            @Qualifier("streamExecutor") Executor streamExecutor
    ) {
        this.providerRouter = providerRouter;
        this.streamExecutor = streamExecutor;
    }

    @PostMapping("/chat/completions")
        public Object chat(
            @Valid @RequestBody ChatRequest request,
            HttpServletRequest httpRequest
        ) {
        Object tenantAttribute = httpRequest.getAttribute("tenantId");
        if (!(tenantAttribute instanceof Long tenantId)) {
            throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Tenant context is missing"
            );
        }

        if (request.stream()) {
            return stream(request, tenantId);
        }

        ChatResponse response = providerRouter.chat(request, tenantId);
        return ResponseEntity.ok()
                .header("X-Request-Id", response.id())
                .body(response);
    }

    private SseEmitter stream(ChatRequest request, long tenantId) {
        SseEmitter emitter = new SseEmitter(300_000L);
        streamExecutor.execute(() -> {
            try {
                providerRouter.stream(request, tenantId, data -> send(emitter, data));
                emitter.send(SseEmitter.event().data("[DONE]"));
                emitter.complete();
            } catch (Exception exception) {
                emitter.completeWithError(exception);
            }
        });
        return emitter;
    }

    private void send(SseEmitter emitter, String data) {
        try {
            emitter.send(SseEmitter.event().data(data));
        } catch (IOException exception) {
            throw new IllegalStateException("Client disconnected from stream", exception);
        }
    }
}
