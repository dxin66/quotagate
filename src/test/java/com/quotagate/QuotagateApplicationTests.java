package com.quotagate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest
@AutoConfigureMockMvc
class QuotagateApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void chatReturnsOpenAiStyleResponse() throws Exception {
		mockMvc.perform(post("/v1/chat/completions")
				.header("Authorization", "Bearer qg_sk_test123")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"model\":\"gpt-standard\",\"messages\":[{\"role\":\"user\",\"content\":\"hello\"}],\"stream\":false}"))
				.andExpect(status().isOk())
				.andExpect(header().exists("X-Request-Id"))
				.andExpect(jsonPath("$.object").value("chat.completion"))
				.andExpect(jsonPath("$.choices[0].message.role").value("assistant"))
				.andExpect(jsonPath("$.choices[0].finish_reason").value("stop"))
				.andExpect(jsonPath("$.usage.total_tokens").isNumber());
	}

	@Test
	void chatRequiresApiKey() throws Exception {
		mockMvc.perform(post("/v1/chat/completions")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"model\":\"gpt-standard\",\"messages\":[{\"role\":\"user\",\"content\":\"hello\"}]}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error.message").value("Invalid API key"));
	}

	@Test
	void chatStreamsOpenAiCompatibleEvents() throws Exception {
		var result = mockMvc.perform(post("/v1/chat/completions")
				.header("Authorization", "Bearer qg_sk_test123")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"model\":\"gpt-standard\",\"messages\":[{\"role\":\"user\",\"content\":\"hello\"}],\"stream\":true}"))
				.andExpect(request().asyncStarted())
				.andReturn();

		mockMvc.perform(asyncDispatch(result))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
				.andExpect(content().string(containsString("chat.completion.chunk")))
				.andExpect(content().string(containsString("[DONE]")));
	}

	@Test
	void listsEnabledModelAliases() throws Exception {
		mockMvc.perform(get("/v1/models")
				.header("Authorization", "Bearer qg_sk_test123"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.object").value("list"))
				.andExpect(jsonPath("$.data[0].id").value("gpt-standard"));
	}

	@Test
	void chatRejectsUnknownApiKey() throws Exception {
		mockMvc.perform(post("/v1/chat/completions")
						.header("Authorization", "Bearer qg_sk_unknown")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"model\":\"gpt-standard\",\"messages\":[{\"role\":\"user\",\"content\":\"hello\"}]}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void chatRejectsUnknownModelRoute() throws Exception {
		mockMvc.perform(post("/v1/chat/completions")
				.header("Authorization", "Bearer qg_sk_test123")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"model\":\"unknown-model\",\"messages\":[{\"role\":\"user\",\"content\":\"hello\"}]}"))
				.andExpect(status().isNotFound());
	}

}
