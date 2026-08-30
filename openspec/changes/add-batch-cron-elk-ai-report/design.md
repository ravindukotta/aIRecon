## Context

Base project: Spring Boot 4.1.1 (Java 21, Maven) with `spring-boot-starter-batch`, `spring-boot-starter-data-jpa`, H2 (dev) + PostgreSQL driver, validation, Lombok already present. No batch jobs, scheduling, logging pipeline, or AI integration exist yet. See proposal.md for motivation.

## Goals / Non-Goals

**Goals:**
- One demo Spring Batch job, cron-triggered, DB → CSV, runnable locally with a single `docker compose up` for the ELK side and `./mvnw spring-boot:run` for the app.
- Structured JSON logs shippable to Kibana with minimal custom code.
- An AI-generated, human-readable completion report per job execution, without blocking or altering the job's own success/failure outcome.

**Non-Goals:**
- Production-grade security, auth, or multi-tenant log isolation.
- High-throughput/partitioned/parallel batch processing.
- A UI for browsing reports (files + Kibana search are sufficient for the demo).
- Retry/backoff policies for the AI call beyond a simple try/catch (best-effort only).

## Decisions

### Scheduling: `@Scheduled` + cron property, not Quartz
Spring's built-in `@Scheduled(cron = "${batch.export.cron}")` on a `@Component` that launches the job via `JobLauncher` is sufficient for a single recurring job and avoids adding Quartz's job-store/clustering complexity, which is unnecessary for a demo. `JobLauncher.run` is called with a unique `JobParameters` (timestamp) each firing so Spring Batch's "same parameters = same instance" rule doesn't block re-runs. Alternative considered: `spring-boot-starter-quartz` — rejected as overkill for one job with no clustering/persistence requirement.

### Reader/Writer: `JpaPagingItemReader` + `FlatFileItemWriter`
Source records come from a JPA entity (reuses `spring-boot-starter-data-jpa` already in the project) via `JpaPagingItemReader`, chunked (e.g. chunk size 50) into `FlatFileItemWriter` with a `DelimitedLineAggregator` and a `BeanWrapperFieldExtractor`. Output path is `${batch.export.output-dir}/export-<jobExecutionId>-<timestamp>.csv`, directory created on startup if missing. Alternative considered: `JdbcCursorItemReader` — rejected in favor of `JpaPagingItemReader` since JPA is already the project's persistence layer and paging reads scale better than a held-open cursor for a demo that may run repeatedly.

### Sample source data: seed via `data.sql` / `CommandLineRunner`
A simple `ExportRecord` JPA entity is seeded with demo rows on startup (H2 in-memory or file-based) so the job has something to read without requiring external DB setup, keeping the "clone and run" experience intact.

### Logging: Logback + `logstash-logback-encoder`, shipped via Filebeat → Logstash → Elasticsearch → Kibana
The app writes structured JSON logs to stdout/a log file using `logstash-logback-encoder`'s `LogstashEncoder` (adds MDC fields automatically, so job name / job execution id pushed into MDC around job execution are carried through). A `docker-compose.yml` stands up Elasticsearch, Logstash (reading the app's log file via a mounted volume, or receiving over a TCP appender), and Kibana with a preconfigured index pattern note in docs. Alternative considered: shipping logs directly from the app via a Logback TCP/HTTP appender straight to Logstash — kept as the default because it avoids needing Filebeat as an extra moving part in a demo; documented as swappable for Filebeat if the user wants file-based shipping.

### AI analysis: Spring AI `ChatClient` in a `JobExecutionListener.afterJob`
A `JobExecutionListener` bean's `afterJob(JobExecution)` builds a compact prompt from `JobExecution` data (status, exit status, start/end time, step executions' read/write/skip/commit counts, and any `getAllFailureExceptions()` messages), calls a Spring AI `ChatClient` (`spring-ai-starter-model-openai`, model/key configurable via env vars, since it's the most broadly demoable provider) to get a natural-language summary, and writes the result to `${batch.report.output-dir}/report-<jobExecutionId>.md` plus a single structured log line containing the job execution id and a short summary. The AI call is wrapped in try/catch: failures are logged and do not change the job's own `ExitStatus` (already finalized by the time `afterJob` runs) and do not throw out of the listener. Alternative considered: doing the analysis inside a dedicated final `Step` — rejected because a listener cleanly runs regardless of whether the job COMPLETED or FAILED, whereas a step would be skipped after a failure without extra `Flow` config.

## Risks / Trade-offs

- [Cron misfire / overlap if a run takes longer than the interval] → Guard the launcher with an `AtomicBoolean`/`JobExplorer` check so a new run is skipped (and logged) if the previous execution is still `STARTED`/`STARTING`.
- [AI provider unavailable or unauthenticated in a demo environment] → Report generation failures are caught and logged as WARN; the CSV job's success/failure is unaffected; docs call out that `OPENAI_API_KEY` (or equivalent) must be set for the AI step to work, with a clear fallback message written into the report file when the call fails.
- [ELK stack is heavyweight to run locally] → Documented as optional: without `docker compose up`, the app still runs and logs locally (JSON to console/file); only the "view in Kibana" part requires the stack to be up.
- [Log volume/PII in AI prompts] → Only aggregate execution metrics and exception messages (no raw record data) are sent to the AI model, keeping the prompt small and avoiding sending exported business data to a third-party model.

## Open Questions

None — provider defaults (OpenAI-compatible Spring AI starter), reader/writer choice, and scheduling mechanism are decided above; these can be revisited by editing config, not specs, if the user prefers Ollama or Quartz later.
