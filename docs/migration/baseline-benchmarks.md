# Stage 1 JVM baseline benchmarks

**Status:** UNVERIFIED: the harness has been added, but it could not be compiled or run in the available execution environment.  
**Repository baseline:** `da0bfe6b9d842b2a487613ac51eb25508b164653`  
**Harness commit branch:** `migration/stage-1-hygiene-discovery-baseline-benchmarks`

## Workloads and derived sizes

The harness uses no benchmark library and no new dependency. Each test is skipped by default and runs only when the test JVM has `-Dwatermelon.benchmarks=true`. Each workload has 5 warm-up iterations followed by 20 timed iterations measured with `System.nanoTime()`. The harness prints every raw nanosecond sample, median, mean and a result checksum to the Gradle test output.

The dimensions are read from the current Kotlin source:

| Workload | Derived input | Iteration count |
|---|---|---:|
| Correlator primary | 8 s probe, 40 ms buckets = 200 audio buckets; +/-30 s by 40 ms = 1,501 offsets; 68 s subtitle context = 1,700 buckets | 5 warm-up + 20 timed |
| Correlator expanded | 8 s probe; +/-180 s by 200 ms = 1,801 offsets; 368 s subtitle context = 9,200 buckets | 5 + 20 |
| Correlator refinement | +/-2 s by 40 ms = 101 offsets; same 9,200-bucket expanded context | 5 + 20 |
| Offset consensus | 3 `SyncMeasurement` values | 5 + 20 |
| Offset consensus | 5 `SyncMeasurement` values | 5 + 20 |
| SRT parser | Synthetic 2,000-cue SRT file | 5 + 20 |
| Speech accumulator | 8 s, 16 kHz, mono, PCM16; consume + finish | 5 + 20 |

The probe duration, bucket size, maximum probe count, primary/expanded search ranges, expanded step and refinement radius come from `SubtitleSyncConfig`. The primary/expanded/refinement offset counts are inclusive of both end points. The benchmark tests are deterministic synthetic workloads, not recordings from real media.

## Machine and runtime

| Field | Observed value |
|---|---|
| Execution host | Linux x86_64 container: `Linux c27ba5dbe0de 6.18.57 #1 SMP Tue Oct 20 21:56:04 UTC 2026 x86_64` |
| JVM | OpenJDK `21.0.12.1`, build `21.0.12.1+1-Debian-1`, dated 2026-08-18 |
| Gradle wrapper | Not locally available because the repository could not be cloned into this workspace |
| System Gradle | Not installed; `gradle --version` returned `bash: gradle: command not found` |
| Android device / ART | Not available in this execution environment |

## Commands and results

Requested build/test command:

```text
./gradlew :subtitle-engine:testDebugUnitTest :media-tools:testDebugUnitTest :common-interfaces:test --stacktrace
bash: line 3: ./gradlew: No such file or directory
EXIT_CODE=127
```

The earlier local clone attempt was:

```text
git clone --filter=blob:none --no-checkout https://github.com/SMozaff/Watermelon-MediaPlayer.git /tmp/watermelon-repo
fatal: unable to access 'https://github.com/SMozaff/Watermelon-MediaPlayer.git/': Could not resolve host: github.com
```

No benchmark execution command could be started because neither a repository checkout/wrapper nor a system Gradle installation was present. Accordingly, there are **no raw timing numbers** in this report; none are fabricated. Compilation and runtime behavior of the new test sources remain UNVERIFIED until repository CI or a developer checkout runs them.

To run the harness from a checkout, set the JVM system property for Gradle and its forked test JVMs via `JAVA_TOOL_OPTIONS`:

```bash
JAVA_TOOL_OPTIONS=-Dwatermelon.benchmarks=true ./gradlew :subtitle-engine:testDebugUnitTest --tests '*SubtitleEngineBaselineBenchmarkTest' --stacktrace
JAVA_TOOL_OPTIONS=-Dwatermelon.benchmarks=true ./gradlew :media-tools:testDebugUnitTest --tests '*SpeechLikelihoodAccumulatorBenchmarkTest' --stacktrace
```

The ordinary Stage 1 build/test command remains unchanged and should be run separately. With the property unset, the benchmark tests abort through JUnit assumptions and do not add benchmark work to normal CI.

## Interpretation caveat

These are JVM microbenchmarks. They are **not ART-on-device numbers** and must not be used as proof of Android end-user speedup or as a migration go/no-go by themselves. JVM JIT warm-up, garbage collection, host CPU scheduling, memory hierarchy, Android ART compilation, thermal limits and actual device CPU architecture can all change observed timings.

## Proposed real-device auto-sync timing (not implemented)

Use a debug build on a named mid-range physical Android device. Prepare a fixed corpus of representative local media/subtitle pairs with known expected offsets. For each pair, measure monotonic elapsed time from the app's auto-sync trigger to the final state transition (including sparse media decode, correlator, consensus and persistence). Record device model, Android/API level, build variant, media codec/track, file duration, selected probe positions, cache-hit vs cold path, elapsed time and result category. Repeat each pair enough times to report median and tail latency; keep first-run/cold and warmed runs separate. Do not add telemetry or upload media-derived data: use local debug-only measurement and scrub logs before sharing. The exact device protocol and acceptance threshold require owner review.
