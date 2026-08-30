## Purpose

Gives operators an automatically generated, plain-language explanation of how each batch job run went — highlighting anomalies and outcomes — instead of requiring them to manually interpret raw execution metrics after every run.

## ADDED Requirements

### Requirement: Analysis is triggered on every job completion
The system SHALL trigger an AI-assisted analysis of the job execution after every job completion, regardless of whether the job succeeded or failed.

#### Scenario: Analysis runs after successful job
- **WHEN** the CSV export job completes with status COMPLETED
- **THEN** an AI-assisted analysis of that job execution SHALL be produced

#### Scenario: Analysis runs after failed job
- **WHEN** the CSV export job completes with status FAILED
- **THEN** an AI-assisted analysis of that job execution SHALL still be produced, and SHALL reference the failure

### Requirement: Analysis is based on job execution metrics
The AI-assisted analysis SHALL be derived from the job execution's actual metrics, including status, duration, records read/written/skipped, and any failure exceptions.

#### Scenario: Metrics are reflected in the analysis
- **WHEN** a job execution with a known read count, write count, and duration completes
- **THEN** the generated analysis SHALL reference those figures (or a summary consistent with them) rather than generic boilerplate

### Requirement: A human-readable report is produced and retrievable
The system SHALL persist the generated analysis as a human-readable report associated with the specific job execution, retrievable after the job has finished.

#### Scenario: Report is retrievable after completion
- **WHEN** a job execution has finished and its analysis has been generated
- **THEN** a report file (or equivalent persisted record) identifying that job execution and containing the analysis text SHALL exist and be retrievable

#### Scenario: Report generation is best-effort relative to job outcome
- **WHEN** the AI analysis step itself fails (e.g. the AI service is unavailable)
- **THEN** the underlying batch job's recorded completion status SHALL NOT be altered by the report-generation failure, and the failure SHALL be logged

### Requirement: Report summary is also logged
The system SHALL emit a log entry summarizing the AI-assisted analysis outcome so it is discoverable through the centralized logging pipeline.

#### Scenario: Report summary appears in logs
- **WHEN** the AI-assisted analysis completes for a job execution
- **THEN** a log entry containing the job execution id and a summary of the analysis outcome SHALL be emitted
