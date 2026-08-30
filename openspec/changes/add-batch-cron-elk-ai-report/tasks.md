## 1. Dependencies & Project Setup

- [ ] 1.1 Add `logstash-logback-encoder`, `spring-ai-starter-model-openai`, and (if not implicitly pulled in) `spring-boot-starter-batch`/`spring-boot-starter-data-jpa` versions to `pom.xml`; enable Spring AI BOM via `spring-ai.version` property if required.
- [ ] 1.2 Add `application.yml` properties: `batch.export.cron`, `batch.export.output-dir`, `batch.report.output-dir`, `batch.export.chunk-size`, Spring AI model/API-key properties (env-var backed).
- [ ] 1.3 Add `@EnableScheduling` and `@EnableBatchProcessing` (if required by the Spring Boot Batch autoconfig version) to the main application or a config class.

## 2. Source Data Model & Seed Data

- [ ] 2.1 Create `ExportRecord` JPA entity representing the rows to be exported.
- [ ] 2.2 Add a Flyway/`data.sql`/`CommandLineRunner` seed step that inserts demo rows into H2 on startup if the table is empty.

## 3. Batch Job: DB → CSV

- [ ] 3.1 Implement `JpaPagingItemReader<ExportRecord>` bean (paged, sorted by id) for the read step.
- [ ] 3.2 Implement `FlatFileItemWriter<ExportRecord>` bean writing to `${batch.export.output-dir}/export-<jobExecutionId>-<timestamp>.csv` with a header line and `DelimitedLineAggregator`/`BeanWrapperFieldExtractor`.
- [ ] 3.3 Define the `Step` (chunk-oriented, configurable chunk size) and the `Job` (`exportJob`) wiring reader → writer.
- [ ] 3.4 Ensure the output directory is created on startup if missing.
- [ ] 3.5 Write a unit/integration test verifying: N seeded records produce a CSV with N+1 lines (header + rows), and zero records produce a header-only file.

## 4. Cron Scheduling & Overlap Guard

- [ ] 4.1 Implement a `@Component` with `@Scheduled(cron = "${batch.export.cron}")` that builds unique `JobParameters` (timestamp) and calls `JobLauncher.run(exportJob, params)`.
- [ ] 4.2 Add an overlap guard (e.g. check `JobExplorer` for a running execution of `exportJob`, or an `AtomicBoolean`) that skips and logs a WARN if the previous run is still in progress.
- [ ] 4.3 Write a test that manually invokes the scheduled method twice in a row (simulating overlap) and asserts the second call is skipped/no-ops as expected.

## 5. Structured Logging & ELK Shipping

- [ ] 5.1 Add `logback-spring.xml` configuring `LogstashEncoder` for console/file output, including job name and job execution id as MDC fields when present.
- [ ] 5.2 Populate MDC (`jobExecutionId`, `jobName`) around job launch (e.g. in the scheduler component or a `JobExecutionListener.beforeJob`) and clear it after.
- [ ] 5.3 Add `docker-compose.yml` with Elasticsearch, Logstash (pipeline config reading the app's JSON log output), and Kibana services.
- [ ] 5.4 Add Logstash pipeline config file (`logstash.conf`) and document the Kibana index pattern to create.
- [ ] 5.5 Manually verify: with `docker compose up` running and the app started, a triggered job run's log entries (start, finish) are visible/searchable in Kibana by job execution id.

## 6. AI-Assisted Job Completion Report

- [ ] 6.1 Implement a `JobExecutionListener` bean (`afterJob`) that extracts status, exit status, duration, and per-step read/write/skip/failure counts plus failure exception messages from the completed `JobExecution`.
- [ ] 6.2 Build a compact prompt from the extracted metrics and call a Spring AI `ChatClient` to generate a natural-language analysis of the run.
- [ ] 6.3 Write the analysis to `${batch.report.output-dir}/report-<jobExecutionId>.md`, and emit a structured log line containing the job execution id and a short summary.
- [ ] 6.4 Wrap the AI call and file write in try/catch so failures are logged (WARN) and do not throw out of `afterJob` or alter the job's own recorded status/exit code; write a fallback report noting the AI step failed when it does.
- [ ] 6.5 Register the listener on the `exportJob` definition so it runs for both COMPLETED and FAILED executions.
- [ ] 6.6 Write a test (mocking the `ChatClient`) verifying the listener produces a report file referencing the execution's actual read/write counts, and another test verifying a simulated `ChatClient` failure does not affect the job's final `ExitStatus`.

## 7. Documentation & End-to-End Verification

- [ ] 7.1 Update `README.md`/`HELP.md` with run instructions: `docker compose up -d` (ELK), setting `OPENAI_API_KEY`, `./mvnw spring-boot:run`, how to trigger/observe a run, where CSV/report files land, and how to view logs in Kibana.
- [ ] 7.2 Perform a full manual end-to-end run: cron fires → CSV produced → logs visible in Kibana → AI report file generated and its content matches the run's metrics.
