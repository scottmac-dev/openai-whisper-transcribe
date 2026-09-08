# Testing

## Overview

21 tests across 8 classes, using JUnit 5, Spring Boot Test, AssertJ and Mockito. 

They aim to verify 4 things:
- the API contract holds on every documented status 
- shared state is thread safe for read/write access
- the server handles more than 200 concurrent blocking uploads
- the API credential never escapes into a response or a log

Every test mocks the STT provider using the `TranscriptionService` interface, to allow a custom stubbed
service which can track requests and assist assertions.

The suite needs no `OPENAI_API_KEY` and makes no network calls.

## Running the Suite

```bash
./mvnw test                                    # mvnw.cmd test on Windows
```

Successful run will end with `Tests run: 21, Failures: 0, Errors: 0` and `BUILD SUCCESS`.

**Two `ERROR` stack traces appear mid-run and are expected.** `TranscriptionControllerTest`
injects a `ResourceAccessException` and a `RestClientResponseException` to simulate the 504 and
502 paths, and `ApiExceptionHandler` logs any 5xx with its stack trace attached. The traces
are the logging working, not a failure.

## Test Suite

- **`Assignment1ApiApplicationTests`** — the full context starts and every bean wires
- **`ShutdownRaceTest`** — shutdown is called exactly once under 200 concurrent callers
- **`TokenCounterConcurrencyTest`** — token counts read is thread safe, with count snapshot unfragmented
- **`TranscriptionLoadTest`** — over 200 simultaneous uploads succeed without delay
- **`AdminControllerTest`** — uptime and shutdown statuses and bodies
- **`StatsControllerTest`** — global stats statuses and bodies
- **`TranscriptionControllerTest`** — every documented transcribe status
- **`ApiKeyLeakageTest`** — the credential reaches no response and no log

Each class documents what it asserts.

## Purpose of Test Suite

1) Verify Thread Safety - ensure logic has no race conditions with concurrent usage, preventing duplicate calls,
stale reads, and corrupted shared state.
2) Verify Performance - prove that server meets requirements of gracefully handling > 200 concurrent requests
3) Verify Response Shape - ensure all responses are mapped to the OpenAPI spec including status codes, messages, parameter
types, and correctly reflect the expected behaviour.
4) Verify Security - prove that API key is never logged or returned in responses which would violate user privacy.
5) Prevent Regressions - changes to the code that deprecate performance or break OpenAPI contract will fail suite, prompting changes
which prevent future bugs or regressions.


## Measured Results

### Concurrent Load Test

`TranscriptionLoadTest` run — 250 uploads of 50 KB audio bytes against a stubbed 2 second upstream:

```
load test: requests=250 maxConcurrent=250
```

**Mutation evidence.** Removing `spring.threads.virtual.enabled=true` fails the test as
designed, maxing out at default 200 platform threads:

```
load test: requests=250 maxConcurrent=200
FAILURE: [requests inside the controller simultaneously]
```

**Limitation.** The stub substitutes at the service layer, so this covers the inbound
connector, request handling and multipart parsing under load. It does not exercise the
outbound `RestClient` connection pool in `OpenAiTranscriptionService`.

