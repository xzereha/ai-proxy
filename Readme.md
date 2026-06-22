# Ai Proxy

[![Build and Test](https://github.com/xzereha/ai-proxy/actions/workflows/test.yml/badge.svg)](https://github.com/xzereha/ai-proxy/actions/workflows/test.yml)
[![Style Check](https://github.com/xzereha/ai-proxy/actions/workflows/style.yml/badge.svg)](https://github.com/xzereha/ai-proxy/actions/workflows/style.yml)

Spring Boot application that acts as a secure proxy to the OpenAI API, with a defensive reliability layer handling timeouts, rate limits, and malformed responses.

## Prerequisites

- **Java 26** (JDK)
- **Docker** (optional, for containerized builds)
- **OpenAI API key**

## Configuration

Set the following environment variables:

| Variable      | Description                                 |
| ------------- | ------------------------------------------- |
| `openai.key`  | OpenAI API key                              |
| `VAULT_TOKEN` | HashiCorp Vault token (if Vault is enabled) |

Additional settings in `application.yaml`:

| Key                                | Default                                        | Description                           |
| ---------------------------------- | ---------------------------------------------- | ------------------------------------- |
| `openai.base-url`                  | `https://api.openai.com/v1/`                   | OpenAI API base URL                   |
| `openai.connect-timeout-ms`        | `2000`                                         | Connection timeout in milliseconds    |
| `openai.response-timeout-seconds`  | `8`                                            | Response timeout in seconds           |
| `openai.retry.max-attempts`        | `3`                                            | Max retries on 429 rate limit         |
| `openai.retry.initial-delay-ms`    | `1000`                                         | Initial backoff delay in milliseconds |
| `openai.retry.max-delay-ms`        | `8000`                                         | Maximum backoff delay                 |
| `openai.response.fallback-message` | `I'm sorry, I couldn't process that response.` | Fallback on parse failure             |
| `openai.response.max-length`       | `4000`                                         | Max response character length         |

## Build

```sh
./gradlew build
```

## Test

```sh
./gradlew test
```

Test reports are generated at `build/reports/tests/test/index.html`.

## Run

```sh
export openai.key=your-key-here
./gradlew bootRun
```

## Docker

```sh
docker build -t ai-proxy .
docker run -p 8080:8080 -e openai.key=sk-your-key-here ai-proxy
```

## API

| Method | Path             | Description           |
| ------ | ---------------- | --------------------- |
| `GET`  | `/api/v1/models` | List available models |
| `POST` | `/api/v1/chat`   | Send a chat message   |

Full API documentation at `/swagger-ui` when running.
