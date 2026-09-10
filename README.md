# aireconsile

Reference Spring Batch demo: a cron-scheduled job reads records from a database, exports them to
CSV, ships structured logs to an ELK stack, and uses Spring AI to generate a natural-language
completion report for every run.

## What it does

- **`exportJob`** (Spring Batch): reads `ExportRecord` rows from H2 (seeded with 5 demo rows on
  startup) and writes them to a timestamped CSV file.
- **Scheduling**: `ExportJobScheduler` fires `exportJob` on a configurable cron expression, with an
  overlap guard so a slow run is never launched twice concurrently.
- **Logging**: structured JSON logs (Logback + `logstash-logback-encoder`), correlated by job name /
  job execution id, optionally shipped to Logstash → Elasticsearch → Kibana.
- **AI report**: on every job completion (success or failure), `AiJobCompletionReportListener` asks a
  Spring AI `ChatClient` (OpenAI) for a plain-language summary of the run and writes it to a report
  file, falling back to a note in the report if the AI call fails — this never affects the job's own
  recorded status.

## Prerequisites

- JDK 25
- Docker (for the optional ELK stack)
- An OpenAI API key (for the optional AI report step)

## Quick start (CSV export only, no ELK, no AI)

```bash
./mvnw spring-boot:run
```

The app starts, seeds 5 demo rows into H2, and `exportJob` fires on its cron schedule (default: every
5 minutes — see `batch.export.cron` below). CSV files land in `./output/export/`. Without an
`OPENAI_API_KEY`, the AI report step fails fast and falls back to a note in
`./output/report/report-<jobExecutionId>.md`; the CSV export itself is unaffected.

The application listens on port `8080` and exposes Kubernetes-compatible probes:

- `GET /health/live` returns `200` while the application is running.
- `GET /health/ready` returns `200` when the database is reachable, or `503` otherwise.

Example deployment probe configuration:

```yaml
livenessProbe:
  httpGet:
    path: /health/live
    port: 8080
readinessProbe:
  httpGet:
    path: /health/ready
    port: 8080
```

## Full run (with ELK logging and AI reports)

1. Start the ELK stack:

   ```bash
   docker compose up -d
   ```

   This starts Elasticsearch (`:9200`), Logstash (TCP log input on `:5000`), and Kibana (`:5601`).
   Wait for `docker compose ps` to show Elasticsearch as healthy before starting the app.

2. Set your OpenAI key and enable ELK log shipping, then run the app:

   ```bash
   export OPENAI_API_KEY=sk-...
   export ELK_LOGGING_ENABLED=true
   ./mvnw spring-boot:run
   ```

   `ELK_LOGGING_ENABLED=true` turns on the `LOGSTASH_TCP` log appender (otherwise it's a no-op, so
   local runs and tests don't pay for a TCP connection to a Logstash that isn't there). Logs still
   print as JSON to the console/`logs/aireconsile.json.log` either way.

3. Trigger/observe a run. By default `exportJob` fires every 5 minutes; to see it faster, override
   the cron expression, e.g. every 10 seconds:

   ```bash
   export BATCH_EXPORT_CRON="*/10 * * * * *"
   ./mvnw spring-boot:run
   ```

4. Check the output:
   - **CSV**: `./output/export/export-<jobExecutionId>-<timestamp>.csv`
   - **AI report**: `./output/report/report-<jobExecutionId>.md`
   - **Logs in Kibana**: open <http://localhost:5601>, go to Stack Management → Data Views (or
     Discover, which will prompt you), and create a data view with index pattern
     `aireconsile-logs-*` and time field `@timestamp`. Then search `jobExecutionId:<id>` in Discover
     to see every log line from one run (job started/finished, step execution, AI report summary).
     You can also query Elasticsearch directly:
     `curl "http://localhost:9200/aireconsile-logs-*/_search?q=jobExecutionId:1&pretty"`.

5. Stop the ELK stack when done: `docker compose down` (add `-v` to also drop the Elasticsearch
   data volume).

## Configuration

All of the following are environment-variable-backed (`application.yml`):

| Property | Env var | Default | Purpose |
|---|---|---|---|
| `batch.export.cron` | `BATCH_EXPORT_CRON` | `0 */5 * * * *` | Cron schedule for `exportJob` |
| `batch.export.output-dir` | `BATCH_EXPORT_OUTPUT_DIR` | `./output/export` | CSV output directory |
| `batch.export.chunk-size` | `BATCH_EXPORT_CHUNK_SIZE` | `50` | Batch chunk size |
| `batch.report.output-dir` | `BATCH_REPORT_OUTPUT_DIR` | `./output/report` | AI report output directory |
| `spring.ai.openai.api-key` | `OPENAI_API_KEY` | (none) | OpenAI API key for the AI report step |
| `spring.ai.openai.chat.options.model` | `OPENAI_MODEL` | `gpt-4o-mini` | OpenAI chat model |
| — | `ELK_LOGGING_ENABLED` | `false` | Enables the Logstash TCP log appender |
| — | `LOGSTASH_HOST` / `LOGSTASH_PORT` | `localhost` / `5000` | Logstash TCP destination |

## Running tests

```bash
./mvnw test
```

Tests do not require Docker or an OpenAI key — the AI-dependent test class mocks `ChatClient`, and
ELK log shipping is off by default.

## Deployment image tracking

On pushes to `master`, the Production promotion job builds the container, publishes
`ghcr.io/<lowercase-owner>/aireconsile:<commit-sha>`, then commits that image reference
to `charts/aireconsile/values-prod.yaml` on `master`. Argo CD renders the Helm chart
at `charts/aireconsile` with these production values and automatically deploys
the committed image. It also corrects configuration drift; automatic pruning is disabled.

The workflow uses GitHub's automatic `GITHUB_TOKEN` with `contents: write` and
`packages: write`; no additional token secret is required. The repository's branch
rules must permit this workflow to push to `master`. If pull requests are required,
the image-update step needs a PR-based promotion flow instead. Token-generated pushes
do not trigger another CI run.

Production jobs run one at a time. If `master` has advanced since a run started, its
image-update step fails rather than publishing an older deployment configuration;
use the run for the latest `master` commit. Pushes never force overwrite remote changes.
The self-hosted runner builds the image and updates the production Helm image values
without connecting to Kubernetes. CI no longer applies resources,
waits for a rollout, or runs a health smoke test. A successful CI run confirms image
publication and promotion in Git; verify the reported manifest commit is Synced and
Healthy in Argo CD to confirm deployment. Kubernetes continues to run the configured
startup, readiness, and liveness probes. The GHCR package is public, so image-pull
credentials are unnecessary.

To activate automatic deployment, commit the chart, Application, and workflow changes
to `master` and let any
older direct-deployment jobs finish, then apply the Application configuration once:

```bash
kubectl --context docker-desktop apply -f argocd/aireconsile.yaml
```

This bootstrap command updates Argo CD's configuration. The Application watches only
`charts/aireconsile/`, so changes under `argocd/` must be applied separately. Subsequent image updates
are deployed automatically. After the next promotion, check the image and manifest
commit in the GitHub job summary against Argo CD's deployed image and synced revision.
