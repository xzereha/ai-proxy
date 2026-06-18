# Reliability Assessment - AI Integration

## 1. Prompt Strategy

### System Instructions

The system prompt instructs the AI to act as a helpful, concise chatbot and to always return a raw JSON object matching a predefined schema. The key design decisions are:

- **Role-based separation**: The system role sets behavioral constraints while the user role carries dynamic input, preventing the user from overriding core instructions (prompt injection mitigation).
- **Explicit JSON enforcement**: The instruction _"Do NOT include markdown formatting (like ```json), code fences, or conversational text. Return ONLY the raw JSON object."_ is included to prevent the AI from wrapping output in markdown code blocks, which would break downstream JSON parsing.
- **JSON Schema enforcement via API**: OpenAI's `json_schema` type with `strict: true` is used in the request's `text.format` field. This instructs the model to produce output that conforms to the schema structurally. The schema defines a single `response` string field.
- **Low temperature (0.1)**: A temperature of 0.1 is hardcoded in `ChatRequestDTO` via `temperature(0.1)` to prioritize deterministic, repeatable output over creative variation.

### Schema

The enforced JSON schema (`OpenAIClient.java:185-196`) is:

```json
{
    "type": "object",
    "properties": {
        "response": { "type": "string" }
    },
    "required": ["response"],
    "additionalProperties": false
}
```

---

## 2. Error Mitigation Strategy

### Timeouts

Configured in `AppConfig.java` via the WebClient builder:

| Config key                        | Value | Purpose                                              |
| --------------------------------- | ----- | ---------------------------------------------------- |
| `openai.connect-timeout-ms`       | 2000  | Maximum time to establish a TCP connection           |
| `openai.response-timeout-seconds` | 8     | Maximum time to wait for a response after connecting |

These are set using Netty's `ChannelOption.CONNECT_TIMEOUT_MILLIS` and reactor's `responseTimeout`. If the AI provider hangs, the connection is cleanly aborted without tying up server threads indefinitely.

### Rate Limits - 429 Exponential Backoff

Implemented in `OpenAIClient.buildRetry()` using reactor's `Retry.backoff`:

| Config key                      | Value | Purpose                                      |
| ------------------------------- | ----- | -------------------------------------------- |
| `openai.retry.max-attempts`     | 3     | Maximum retry count                          |
| `openai.retry.initial-delay-ms` | 1000  | Initial backoff delay (doubles each attempt) |
| `openai.retry.max-delay-ms`     | 8000  | Ceiling for backoff delay                    |

The backoff filter targets only `WebClientResponseException` with HTTP 429 (`Too Many Requests`). Each retry logs a warning. After exhausting retries, the exception propagates to the caller.

### Hallucination & Parse Failure Mitigation

Implemented in `OpenAIClient.parseAndValidate()` as a three-layer safety net:

1. **JSON Parsing**: `ObjectMapper.readValue()` parses the AI response into `MessageResponseDTO`. If parsing fails (malformed JSON, extra text, etc.), the exception is caught and a fallback DTO is returned instead of crashing.
2. **Bean Validation**: The parsed DTO is validated with `jakarta.validation.Validator` against `@NotNull @NotBlank` constraints on the `response` field. If violations exist, the fallback DTO is used.
3. **Length Guard**: If the response exceeds `openai.response.max-length` (4000 characters), the fallback is returned.

The fallback message is configurable via `openai.response.fallback-message`.

### Status Handling

The OpenAI API's `/v1/responses` endpoint can return various statuses. The `OpenAIClient.getResponse()` method uses a switch expression (`OpenAIClient.java:89-114`) to handle each:

| Status        | Handling                                                    |
| ------------- | ----------------------------------------------------------- |
| `completed`   | Parse and validate output                                   |
| `failed`      | Throw `ModelFailedException`                                |
| `in_progress` | Throw `IllegalStateException` (unexpected in non-streaming) |
| `cancelled`   | Throw `IllegalStateException` (unexpected in non-streaming) |
| `incomplete`  | Throw `ModelIncompleteException`                            |

A content refusal (detected via `extractRefusal()`) throws `ModelRefusalException`.

---

## 3. Reliability Assessment

### LLM Limitations in Production

| Limitation                            | Impact                                                      | How the Architecture Addresses It                                                                                                   |
| ------------------------------------- | ----------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------- |
| **Non-deterministic output**          | Same prompt can yield different results each call           | Low temperature (0.1), `json_schema` with strict mode, and fallback on validation failure                                           |
| **Latency variance**                  | Response times can range from <1s to >30s                   | Strict connect (2s) and read (8s) timeouts prevent thread exhaustion                                                                |
| **Rate limiting**                     | Provider returns HTTP 429 under load                        | Exponential backoff with configurable retry count and delay avoids request drops and self-heals                                     |
| **Hallucinations / malformed output** | Model may return plausible-sounding but invalid JSON        | `parseAndValidate()` catches parse errors, Bean Validation violations, and excessive length - all result in a safe fallback         |
| **Content refusal**                   | Model may refuse to answer, returning a refusal object      | `ModelRefusalException` is thrown and can be mapped to an appropriate HTTP response by the controller                               |
| **Model unavailability / errors**     | Provider API may return 5xx or a `failed` status            | Custom exceptions propagate cleanly; retry handles transient failures                                                               |
| **Prompt injection**                  | Malicious user input could override system instructions     | Role separation (system vs. user), strict JSON schema, and the explicit "do not include markdown" instruction reduce attack surface |
| **Provider dependency**               | Application cannot function without the external AI service | Fail-fast on startup if API key is missing; timeouts, retries, and fallbacks provide graceful degradation                           |

### Production Readiness Gaps

- **No circuit breaker**: Repeated provider failures still result in retries. Adding a circuit breaker (e.g., Resilience4j) would prevent cascading failures.
- **No response caching**: Repeated identical prompts trigger fresh API calls. A result cache (keyed on prompt hash) would reduce cost and latency.
- **No monitoring/alerting**: Parse failures, retries, and fallbacks are logged but not exposed as metrics. In production, these should feed into a monitoring system.
- **Singleton model**: Currently using `gpt-4o` hardcoded in the service layer. A production system would select models dynamically based on task complexity and cost.
