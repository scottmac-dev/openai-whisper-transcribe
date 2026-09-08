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

### Client Side Optimisations

Client side audio recording and compression has been optimised for single user voice input which has
reduced file upload size and improved UI responsiveness.

This was tested manually by recording the same audio input multiple times and recording the results.

The optimisations applied in commit `5c8dfe7` where:
- **Mono capture** (`channelCount: 1`) — one voice signal, halving the data a stereo source would produce with default count 2
- **16 kHz sample rate** — the speech band, down from the 48 kHz default resulting in less samples recorded overall 
- **Opus at 24 kbps** (`audioBitsPerSecond`) — the encoder setting that actually determines upload size
- **Echo cancellation, noise suppression and auto gain** — a cleaner signal, which buys transcript accuracy rather than bytes
- **1 second recorder timeslice** — audio is encoded during recording instead of in one batch at stop, so the upload starts sooner, empty chunks are discarded
- **Polling paused while the tab is hidden** — no uptime or stats requests for a UI nobody is looking at

The results of testing before and after optimisation are displayed below. Each figure is read
straight off the usage metrics panel after recording the same passage of speech.

**Before**

| Test | Record time (s) | Audio sent (KB) | Response time (s) | Words (total) |
| --- | --- | --- | --- | --- |
| 1 | 8.6 | 130.5 | 1.09 | 10 |
| 2 | 9.6 | 146.5 | 1.02 | 10 |
| 3 | 9.3 | 140.8 | 0.99 | 10 |
| 4 | 9.8 | 149.3 | 1.07 | 10 |
| 5 | 9.4 | 141.8 | 1.06 | 10 |
| **Average** | **9.34** | **141.78** | **1.05** | **10** |

**After**

| Test | Record time (s) | Audio sent (KB) | Response time (s) | Words (total) |
| --- | --- | --- | --- | --- |
| 1 | 9.0 | 25.4 | 1.09 | 10 |
| 2 | 8.9 | 23.9 | 1.10 | 10 |
| 3 | 9.3 | 25.4 | 1.11 | 10 |
| 4 | 9.6 | 26.1 | 1.03 | 10 |
| 5 | 9.4 | 25.4 | 1.05 | 10 |
| **Average** | **9.24** | **25.24** | **1.08** | **10** |

**Findings**

- **Upload size fell 5.6x**, 141.78 KB to 25.24 KB, an 82% reduction.
- **Record time is unchanged** (9.34 s vs 9.24 s), confirming the two sets are measuring the
  same passage of speech and the size difference is the encoder, not a shorter recording.
- **Response time is unchanged within noise** (1.05 s to 1.08 s, +0.03 s). Upload size is not
  the bottleneck on a local connection. The saving is in bandwidth consumed, which matters on a mobile 
  or metered connection.
- **Transcript accuracy is unaffected**: 10 words returned on all ten runs, so the compression
  costs nothing in output quality.
 
 **Further Investigations**
 
 Below a larger length recording was tested to see if the bandwidth savings would reduce response latency.
 
| Test | Record time (s) | Audio sent (KB) | Response time (s) | Words (total) |
| --- | --- | --- | --- | --- |
| BEFORE | 59.2 | 960.0 | 4.73 | 59 |
| AFTER | 57.0 | 159.8 | 3.07 | 59 |

- **The size reduction holds at length**: 960.0 KB to 159.8 KB, 6.0x smaller, the same encoder 
behaviour the short test showed, so the saving scales with duration rather than tapering off.
- **Response time did improve here**, 4.73 s to 3.07 s, 1.66 s faster. The recording was 3.7%
  shorter which does not justify a 35% drop, so the upload size itself is the plausible cause
  for the latency saving.
- **Accuracy is unaffected at length too**: 59 words returned either way.

Caveat: this is a single paired run, against five each for the short test, so it indicates the
trend rather than establishing it. Provider-side variance alone could account for a meaningful
share of a 1.66 s difference.

