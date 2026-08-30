## Why

We need a runnable reference implementation showing how a Spring Batch job can be scheduled on a cron cycle, move data from a database to a flat file, ship its logs to a centralized ELK stack for observability, and automatically produce an AI-generated summary of each run's outcome — so the pattern can be reused as a template for future batch integrations.

## What Changes

- Add a scheduled Spring Batch job (cron-triggered) that reads records from the H2 database via a paging/cursor `ItemReader` and writes them to a timestamped CSV file via `FlatFileItemWriter`.
- Add structured JSON logging (Logback + `logstash-logback-encoder`) and a Docker Compose stack (Elasticsearch, Logstash, Kibana) so application and batch-step logs are shipped and searchable in Kibana.
- Add a `JobExecutionListener` that, on `afterJob`, gathers `JobExecution` metrics (status, read/write/skip/failure counts, duration, exceptions) and calls a Spring AI `ChatClient` to produce a natural-language analysis, then persists/writes it as a job completion report (Markdown/text file, plus a log entry so it also flows to Kibana).
- Add configuration/documentation for running the whole demo end-to-end (start ELK via Docker Compose, run the app, trigger/await the scheduled job, view the report and the logs in Kibana).

## Capabilities

### New Capabilities
- `batch-csv-export-job`: Cron-scheduled Spring Batch job that reads records from the database and writes them to a CSV file, including job/step configuration, chunking, and scheduling.
- `observability-logging`: Structured JSON application/batch logging shipped to an ELK stack (Elasticsearch, Logstash, Kibana) via Docker Compose, viewable in Kibana.
- `ai-job-completion-report`: Spring AI–assisted analysis of each job execution's outcome, triggered on job completion, producing and storing a human-readable report and logging a summary event.

### Modified Capabilities
(none — this is a new demo project with no existing specs)

## Impact

- New Maven dependencies: `spring-boot-starter-quartz` or `spring-boot-starter` scheduling (`@EnableScheduling`), `logstash-logback-encoder`, `spring-ai-starter-model-openai` (or `spring-ai-starter-model-ollama`).
- New source entity + seed data for the DB-backed read step (H2, already present).
- New `docker-compose.yml` for Elasticsearch/Logstash/Kibana (Filebeat optional).
- New config: `application.yml` batch job/cron properties, `logback-spring.xml`, Spring AI chat model properties (API key/base-url via env var).
- New output artifacts at runtime: CSV export files and job-completion report files under a configurable output directory.
