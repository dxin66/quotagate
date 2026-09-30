package com.quotagate.provider;

import com.quotagate.api.dto.ChatRequest;
import com.quotagate.api.dto.ChatResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiProviderTest {

    @Test
    void chatFormatsOpenAiRequestAndMapsResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo("https://api.example.com/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(content().json("""
                        {
                          "model": "gpt-4o-mini",
                          "messages": [{"role": "user", "content": "hello"}],
                          "stream": false
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "id": "chatcmpl-123",
                          "object": "chat.completion",
                          "created": 1710000000,
                          "model": "gpt-4o-mini",
                          "choices": [{
                            "index": 0,
                            "message": {"role": "assistant", "content": "hello back"},
                            "finish_reason": "stop"
                          }],
                          "usage": {
                            "prompt_tokens": 10,
                            "completion_tokens": 5,
                            "total_tokens": 15
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        OpenAiProperties properties = new OpenAiProperties();
        properties.setApiKey("test-key");
        properties.setBaseUrl("https://api.example.com/v1");
        properties.setDefaultModel("gpt-4o-mini");

        OpenAiProvider provider = new OpenAiProvider(
                builder.baseUrl("https://api.example.com/v1").build(),
                properties
        );

        ChatResponse response = provider.chat(new ChatRequest(
                "gpt-4o-mini",
                List.of(new ChatRequest.Message("user", "hello")),
                false
        ));

        assertEquals("chatcmpl-123", response.id());
        assertEquals("hello back", response.choices().getFirst().message().content());
        assertEquals(15, response.usage().totalTokens());
        server.verify();
    }

    @Test
    void streamRelaysChunksAndReturnsFinalUsage() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo("https://api.example.com/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {
                          "model": "gpt-4o-mini",
                          "messages": [{"role": "user", "content": "hello"}],
                          "stream": true,
                          "stream_options": {"include_usage": true}
                        }
                        """))
                .andRespond(withSuccess("""
                        data: {"id":"chatcmpl-stream","object":"chat.completion.chunk","choices":[{"index":0,"delta":{"content":"hello"},"finish_reason":null}]}

                        data: {"id":"chatcmpl-stream","object":"chat.completion.chunk","choices":[],"usage":{"prompt_tokens":10,"completion_tokens":2,"total_tokens":12}}

                        data: [DONE]

                        """, MediaType.TEXT_EVENT_STREAM));

        OpenAiProperties properties = new OpenAiProperties();
        properties.setApiKey("test-key");
        properties.setBaseUrl("https://api.example.com/v1");
        OpenAiProvider provider = new OpenAiProvider(
                builder.baseUrl("https://api.example.com/v1").build(), properties);

        List<String> chunks = new ArrayList<>();
        ProviderStreamResult result = provider.stream(new ChatRequest(
                "gpt-4o-mini",
                List.of(new ChatRequest.Message("user", "hello")),
                true
        ), chunks::add);

        assertEquals(2, chunks.size());
        assertEquals("chatcmpl-stream", result.requestId());
        assertEquals(12, result.usage().totalTokens());
        server.verify();
    }
}
