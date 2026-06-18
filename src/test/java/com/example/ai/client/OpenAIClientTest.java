package com.example.ai.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.example.ai.config.OpenAiConfig;
import com.example.ai.exception.ModelFailedException;
import com.example.ai.exception.ModelIncompleteException;
import com.example.ai.exception.ModelRefusalException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.validation.Validation;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.test.StepVerifier;

import java.io.IOException;

class OpenAIClientTest {

    private MockWebServer mockWebServer;
    private OpenAIClient client;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        var openAiConfig = new OpenAiConfig();
        var webClient = WebClient.builder().build();
        var objectMapper = new ObjectMapper();
        var validator = Validation.buildDefaultValidatorFactory().getValidator();

        client = new OpenAIClient(
                openAiConfig,
                webClient,
                objectMapper,
                validator,
                3,
                100L,
                8000L,
                "Fallback response.",
                4000,
                mockWebServer.url("/v1/").toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void getResponse_shouldReturnParsedDtoOnSuccess() {
        var responseJson = """
                {
                  "id": "resp_1",
                  "object": "response",
                  "created_at": 1000,
                  "status": "completed",
                  "model": "gpt-4o",
                  "output": [
                    {
                      "id": "out_1",
                      "type": "message",
                      "role": "assistant",
                      "content": [
                        {
                          "type": "output_text",
                          "text": "{\\"response\\":\\"Hello!\\"}",
                          "refusal": null,
                          "annotations": []
                        }
                      ]
                    }
                  ],
                  "usage": {
                    "input_tokens": 10,
                    "output_tokens": 5,
                    "output_tokens_details": { "reasoning_tokens": 0 },
                    "total_tokens": 15
                  }
                }
                """;

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(responseJson));

        var result = client.getResponse("gpt-4o", "Hi").block();

        assertEquals("Hello!", result.response());
    }

    @Test
    void getResponse_shouldReturnFallbackOnMalformedJson() {
        var responseJson = """
                {
                  "id": "resp_1",
                  "object": "response",
                  "created_at": 1000,
                  "status": "completed",
                  "model": "gpt-4o",
                  "output": [
                    {
                      "id": "out_1",
                      "type": "message",
                      "role": "assistant",
                      "content": [
                        {
                          "type": "output_text",
                          "text": "Sure, here is your summary...",
                          "refusal": null,
                          "annotations": []
                        }
                      ]
                    }
                  ],
                  "usage": {
                    "input_tokens": 10,
                    "output_tokens": 5,
                    "output_tokens_details": { "reasoning_tokens": 0 },
                    "total_tokens": 15
                  }
                }
                """;

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(responseJson));

        var result = client.getResponse("gpt-4o", "Hi").block();

        assertEquals("Fallback response.", result.response());
    }

    @Test
    void getResponse_shouldReturnFallbackOnEmptyResponse() {
        var responseJson = """
                {
                  "id": "resp_1",
                  "object": "response",
                  "created_at": 1000,
                  "status": "completed",
                  "model": "gpt-4o",
                  "output": [],
                  "usage": {
                    "input_tokens": 10,
                    "output_tokens": 0,
                    "output_tokens_details": { "reasoning_tokens": 0 },
                    "total_tokens": 10
                  }
                }
                """;

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(responseJson));

        var result = client.getResponse("gpt-4o", "Hi").block();

        assertEquals("Fallback response.", result.response());
    }

    @Test
    void getResponse_shouldThrowOnRefusal() {
        var responseJson = """
                {
                  "id": "resp_1",
                  "object": "response",
                  "created_at": 1000,
                  "status": "completed",
                  "model": "gpt-4o",
                  "output": [
                    {
                      "id": "out_1",
                      "type": "message",
                      "role": "assistant",
                      "content": [
                        {
                          "type": "refusal",
                          "text": null,
                          "refusal": "I cannot answer that.",
                          "annotations": []
                        }
                      ]
                    }
                  ],
                  "usage": {
                    "input_tokens": 10,
                    "output_tokens": 5,
                    "output_tokens_details": { "reasoning_tokens": 0 },
                    "total_tokens": 15
                  }
                }
                """;

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(responseJson));

        StepVerifier.create(client.getResponse("gpt-4o", "Bad question"))
                .expectErrorSatisfies(e -> {
                    assertInstanceOf(ModelRefusalException.class, e);
                    assertEquals("I cannot answer that.",
                            ((ModelRefusalException) e).getRefusal());
                })
                .verify();
    }

    @Test
    void getResponse_shouldThrowOnFailedStatus() {
        var responseJson = """
                {
                  "id": "resp_1",
                  "object": "response",
                  "created_at": 1000,
                  "status": "failed",
                  "model": "gpt-4o",
                  "output": [],
                  "usage": {
                    "input_tokens": 10,
                    "output_tokens": 0,
                    "output_tokens_details": { "reasoning_tokens": 0 },
                    "total_tokens": 10
                  }
                }
                """;

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(responseJson));

        StepVerifier.create(client.getResponse("gpt-4o", "Hi"))
                .expectError(ModelFailedException.class)
                .verify();
    }

    @Test
    void getResponse_shouldThrowOnIncompleteStatus() {
        var responseJson = """
                {
                  "id": "resp_1",
                  "object": "response",
                  "created_at": 1000,
                  "status": "incomplete",
                  "model": "gpt-4o",
                  "output": [],
                  "usage": {
                    "input_tokens": 10,
                    "output_tokens": 0,
                    "output_tokens_details": { "reasoning_tokens": 0 },
                    "total_tokens": 10
                  }
                }
                """;

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(responseJson));

        StepVerifier.create(client.getResponse("gpt-4o", "Hi"))
                .expectError(ModelIncompleteException.class)
                .verify();
    }

    @Test
    void getResponse_shouldRetryOn429AndSucceed() {
        var successJson = """
                {
                  "id": "resp_2",
                  "object": "response",
                  "created_at": 1000,
                  "status": "completed",
                  "model": "gpt-4o",
                  "output": [
                    {
                      "id": "out_1",
                      "type": "message",
                      "role": "assistant",
                      "content": [
                        {
                          "type": "output_text",
                          "text": "{\\"response\\":\\"Retried successfully!\\"}",
                          "refusal": null,
                          "annotations": []
                        }
                      ]
                    }
                  ],
                  "usage": {
                    "input_tokens": 10,
                    "output_tokens": 5,
                    "output_tokens_details": { "reasoning_tokens": 0 },
                    "total_tokens": 15
                  }
                }
                """;

        // Use a client with very short retry delays for fast tests
        var fastRetryClient = new OpenAIClient(
                new OpenAiConfig(),
                WebClient.builder().build(),
                new ObjectMapper(),
                Validation.buildDefaultValidatorFactory().getValidator(),
                3,
                10L,
                100L,
                "Fallback response.",
                4000,
                mockWebServer.url("/v1/").toString());

        mockWebServer.enqueue(new MockResponse().setResponseCode(429));
        mockWebServer.enqueue(new MockResponse().setResponseCode(429));
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(successJson));

        var result = fastRetryClient.getResponse("gpt-4o", "Hi").block();

        assertEquals("Retried successfully!", result.response());
        assertEquals(3, mockWebServer.getRequestCount());
    }

    @Test
    void getResponse_shouldFailAfterMaxRetries() {
        // Use a client with very short retry delays for fast tests
        var fastRetryClient = new OpenAIClient(
                new OpenAiConfig(),
                WebClient.builder().build(),
                new ObjectMapper(),
                Validation.buildDefaultValidatorFactory().getValidator(),
                3,
                10L,
                100L,
                "Fallback response.",
                4000,
                mockWebServer.url("/v1/").toString());

        mockWebServer.enqueue(new MockResponse().setResponseCode(429));
        mockWebServer.enqueue(new MockResponse().setResponseCode(429));
        mockWebServer.enqueue(new MockResponse().setResponseCode(429));
        mockWebServer.enqueue(new MockResponse().setResponseCode(429));

        StepVerifier.create(fastRetryClient.getResponse("gpt-4o", "Hi"))
                .expectErrorSatisfies(e -> {
                    assertInstanceOf(Throwable.class, e);
                })
                .verify();
    }

    @Test
    void parseAndValidate_shouldReturnFallbackWhenResponseExceedsMaxLength() {
        var longResponse = "a".repeat(5000);
        var dto = client.parseAndValidate("{\"response\":\"" + longResponse + "\"}");

        assertEquals("Fallback response.", dto.response());
    }

    @Test
    void parseAndValidate_shouldReturnDtoWhenResponseWithinMaxLength() {
        var dto = client.parseAndValidate("{\"response\":\"Short response.\"}");

        assertEquals("Short response.", dto.response());
    }
}
