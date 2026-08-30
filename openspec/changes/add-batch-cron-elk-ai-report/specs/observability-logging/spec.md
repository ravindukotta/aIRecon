## Purpose

Ensures application and batch-job logs are emitted in a structured, machine-parseable form and centrally shipped so operators can search, filter, and monitor job runs in Kibana instead of reading local log files.

## ADDED Requirements

### Requirement: Structured log output
The system SHALL emit application and batch job logs in a structured (JSON) format that includes at minimum a timestamp, log level, logger/source, message, and thread/context identifiers.

#### Scenario: Log entry is structured
- **WHEN** any log statement is emitted by the application
- **THEN** the emitted log record SHALL be a structured JSON object containing timestamp, level, logger name, and message fields

### Requirement: Batch job events are logged with correlation context
The system SHALL include job execution identifying context (job name, job execution id, step name where applicable) in log entries emitted during a batch job run.

#### Scenario: Job start and completion are logged
- **WHEN** a batch job execution starts and later finishes
- **THEN** a log entry SHALL be emitted for both the start and the finish, each tagged with the job name and job execution id

#### Scenario: Logs from one run are correlatable
- **WHEN** searching centralized logs by a specific job execution id
- **THEN** all log entries emitted during that single job run SHALL be retrievable using that id

### Requirement: Logs are shipped to a central log store viewable in Kibana
The system SHALL ship its logs to a centralized log pipeline (Elasticsearch) such that they become searchable and viewable in Kibana without manual log file transfer.

#### Scenario: Log becomes searchable in Kibana
- **WHEN** the application emits a log entry while the logging pipeline is running
- **THEN** that log entry SHALL become visible and searchable in Kibana within a short, bounded delay

#### Scenario: Logging pipeline unavailable
- **WHEN** the centralized log store is temporarily unreachable
- **THEN** the application SHALL continue running and processing its batch jobs without crashing, and SHALL NOT lose in-process log records needed for local diagnostics
