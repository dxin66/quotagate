package com.quotagate.provider;

import com.quotagate.api.dto.ChatRequest;
import com.quotagate.api.dto.ChatResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Objects;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Component("openai")
@ConditionalOnExpression("'${quotagate.providers.openai.api-key:}'.length() > 0")
public class OpenAiProvider implements LlmProvider {

    private final RestClient restClient;
    private final OpenAiProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Autowired
    public OpenAiProvider(RestClient.Builder restClientBuilder, OpenAiProperties properties) {
        this(createRestClient(restClientBuilder, properties), properties);
    }

    OpenAiProvider(RestClient restClient, OpenAiProperties properties) {
        this.properties = properties;
        this.restClient = restClient.mutate()
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    private static RestClient createRestClient(RestClient.Builder restClientBuilder, OpenAiProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getTimeoutMs()))
                .build();
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(properties.getTimeoutMs()));
        return restClientBuilder
                .baseUrl(normalizeBaseUrl(properties.getBaseUrl()))
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        String model = Objects.requireNonNullElse(request.model(), properties.getDefaultModel());
        OpenAiChatRequest openAiRequest = new OpenAiChatRequest(
                model,
                request.messages().stream()
                        .map(message -> new OpenAiChatRequest.Message(message.role(), message.content()))
                        .toList(),
                false,
                null
        );

        OpenAiChatResponse response = restClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(openAiRequest)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (httpRequest, clientResponse) -> {
                    throw new IllegalStateException("OpenAI request failed with status " + clientResponse.getStatusCode());
                })
                .body(OpenAiChatResponse.class);

        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new IllegalStateException("OpenAI returned an empty response");
        }

        OpenAiChatResponse.Choice choice = response.choices().getFirst();
        OpenAiChatResponse.Message message = choice.message();
        ChatResponse.Usage usage = new ChatResponse.Usage(
                response.usage().promptTokens(),
                response.usage().completionTokens(),
                response.usage().totalTokens()
        );

        return new ChatResponse(
                response.id(),
                response.object(),
                response.created(),
                response.model(),
                List.of(new ChatResponse.Choice(
                        choice.index(),
                        new ChatResponse.Message(message.role(), message.content()),
                        choice.finishReason()
                )),
                usage
        );
    }

    @Override
    public ProviderStreamResult stream(
            ChatRequest request,
            Consumer<String> chunkConsumer
    ) {
        OpenAiChatRequest openAiRequest = new OpenAiChatRequest(
                request.model(),
                request.messages().stream()
                        .map(message -> new OpenAiChatRequest.Message(
                                message.role(), message.content()))
                        .toList(),
                true,
                new StreamOptions(true)
        );

        return restClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(openAiRequest)
                .exchange((httpRequest, response) -> {
                    if (response.getStatusCode().isError()) {
                        throw new IllegalStateException(
                                "OpenAI request failed with status "
                                        + response.getStatusCode());
                    }

                    AtomicReference<String> requestId = new AtomicReference<>();
                    AtomicReference<ChatResponse.Usage> usage = new AtomicReference<>();
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(
                                    response.getBody(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (!line.startsWith("data:")) {
                                continue;
                            }
                            String data = line.substring("data:".length()).trim();
                            if (data.isEmpty() || "[DONE]".equals(data)) {
                                continue;
                            }
                            JsonNode node = objectMapper.readTree(data);
                            if (node.hasNonNull("id")) {
                                requestId.compareAndSet(null, node.get("id").asText());
                            }
                            JsonNode usageNode = node.get("usage");
                            if (usageNode != null && !usageNode.isNull()) {
                                usage.set(new ChatResponse.Usage(
                                        usageNode.path("prompt_tokens").asInt(),
                                        usageNode.path("completion_tokens").asInt(),
                                        usageNode.path("total_tokens").asInt()
                                ));
                            }
                            chunkConsumer.accept(data);
                        }
                    }

                    if (requestId.get() == null || usage.get() == null) {
                        throw new IllegalStateException(
                                "OpenAI stream ended without request id or usage");
                    }
                    return new ProviderStreamResult(requestId.get(), usage.get());
                });
    }

    private static String normalizeBaseUrl(String baseUrl) {
        return baseUrl.endsWith("/v1") || baseUrl.endsWith("/v1/")
                ? baseUrl.replaceAll("/+$", "")
                : baseUrl.replaceAll("/+$", "") + "/v1";
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record OpenAiChatRequest(
            String model,
            List<Message> messages,
            boolean stream,
            @JsonProperty("stream_options") StreamOptions streamOptions
    ) {
        public record Message(String role, String content) {
        }
    }

    public record StreamOptions(@JsonProperty("include_usage") boolean includeUsage) {
    }

    public record OpenAiChatResponse(
            String id,
            String object,
            long created,
            String model,
            List<Choice> choices,
            Usage usage
    ) {
        public record Choice(int index, Message message, @JsonProperty("finish_reason") String finishReason) {
        }

        public record Message(String role, String content) {
        }

        public record Usage(
                @JsonProperty("prompt_tokens") int promptTokens,
                @JsonProperty("completion_tokens") int completionTokens,
                @JsonProperty("total_tokens") int totalTokens
        ) {
        }
    }
}
