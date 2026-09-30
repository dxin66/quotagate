# Benchmark Results

All numbers below were produced from the current working tree on 2026-09-30.
The HTTP benchmark uses the local Mock provider, so it measures the complete
gateway, quota, event-publishing, and accounting path without external model
network latency.

## End-to-end hot-key load

```bash
k6 run --summary-export benchmark/results/hot-key-50-batched.json \
  -e BASE_URL=http://127.0.0.1:18081 \
  -e API_KEY=qg_sk_test123 \
  -e VUS=50 \
  -e DURATION=10s \
  benchmark/hot-key.js
```

| Requests | Throughput | Average | P95 | Max | Error rate |
| ---: | ---: | ---: | ---: | ---: | ---: |
| 52,553 | 5,251.42 req/s | 9.41 ms | 17.62 ms | 230.39 ms | 0% |

Accounting verification after the run:

| HTTP requests | New MySQL ledger rows | Redis pending entries after 2 s |
| ---: | ---: | ---: |
| 52,553 | 52,553 | 0 |

This equality verifies that every successful gateway request produced exactly
one idempotent usage-ledger record during the measured run.

## Cache concurrency regression

Command:

```bash
./mvnw -q -Dtest=ModelRouteConcurrencyBenchmarkTest test
```

For 500 concurrent lookups, the cache-aside comparison issued 500 database
queries, while the Caffeine single-flight path issued one database query and
served the remaining 499 requests from the local cache. The latest run recorded
63.96 ms P95 for the comparison path and 0.01 ms P95 for the optimized path.

This is an in-process concurrency regression test rather than a JMH benchmark;
it is intended to verify query collapse, not publish a hardware-independent JVM
performance score.
