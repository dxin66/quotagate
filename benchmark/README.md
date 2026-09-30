# QuotaGate Benchmark

The scripts require a running QuotaGate instance, MySQL, Redis, and k6.

```bash
docker compose up -d
./mvnw spring-boot:run
```

Run the normal load at different concurrency levels by changing the target in
`load.js`, or use the hot-key and quota overrides:

```bash
k6 run -e API_KEY=qg_sk_test123 -e VUS=50 benchmark/load.js
k6 run -e API_KEY=qg_sk_test123 -e VUS=100 benchmark/load.js
k6 run -e API_KEY=qg_sk_test123 -e VUS=300 benchmark/load.js
k6 run -e API_KEY=qg_sk_test123 -e VUS=500 benchmark/hot-key.js
k6 run -e API_KEY=qg_sk_test123 -e VUS=1000 benchmark/quota.js
```

For a different server:

```bash
k6 run -e BASE_URL=http://127.0.0.1:18081 -e API_KEY="$QUOTAGATE_API_KEY" benchmark/load.js
```

The hot-key script measures the real gateway path, which uses logical
expiration and single-flight. The cache-aside comparison is performed by
`ModelRouteConcurrencyBenchmarkTest`, because the HTTP API does not expose a
deliberate cache-bypass mode.