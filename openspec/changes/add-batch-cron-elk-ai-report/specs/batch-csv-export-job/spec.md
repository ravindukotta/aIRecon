## Purpose

Provides an automated, cron-scheduled export of database records to CSV files so downstream systems or operators can consume a fresh flat-file extract without manual intervention.

## ADDED Requirements

### Requirement: Cron-scheduled job execution
The system SHALL trigger the CSV export batch job automatically according to a configurable cron expression, without requiring manual invocation.

#### Scenario: Job fires on schedule
- **WHEN** the configured cron expression's trigger time is reached
- **THEN** the system SHALL start a new execution of the CSV export job

#### Scenario: Cron expression is configurable
- **WHEN** the cron expression is changed in configuration and the application is restarted
- **THEN** the job SHALL subsequently fire according to the new schedule

#### Scenario: Overlapping execution is prevented
- **WHEN** the scheduled trigger fires while a previous execution of the same job is still running
- **THEN** the system SHALL NOT start a concurrent duplicate execution of the same job

### Requirement: Read records from the database
The system SHALL read all matching records from the source database table in a memory-efficient, chunked manner during the job's read step.

#### Scenario: All matching records are read
- **WHEN** the job runs against a database containing records matching the export criteria
- **THEN** every matching record SHALL be read exactly once during the job execution

#### Scenario: No matching records
- **WHEN** the job runs and no records match the export criteria
- **THEN** the job SHALL complete successfully having read zero records, and SHALL still produce a CSV file containing only the header row

### Requirement: Write records to a CSV file
The system SHALL write each record read during the job to a CSV file, including a header row, on the local filesystem.

#### Scenario: CSV file is created per run
- **WHEN** a job execution completes its write step
- **THEN** a CSV file SHALL exist containing a header row followed by one row per record read, with a filename that uniquely identifies the run (e.g. includes a timestamp or job execution id)

#### Scenario: Existing output is not overwritten
- **WHEN** two separate job executions occur
- **THEN** each execution's CSV output SHALL be written to a distinct file so earlier output is not overwritten

### Requirement: Job completion status is recorded
The system SHALL record a final status (e.g. COMPLETED or FAILED) and execution metrics (records read, records written, start/end time) for every job execution.

#### Scenario: Successful run status
- **WHEN** the job completes all steps without error
- **THEN** the recorded job execution status SHALL be COMPLETED and SHALL include the count of records read and written

#### Scenario: Failed run status
- **WHEN** the job encounters an unrecoverable error during read or write
- **THEN** the recorded job execution status SHALL be FAILED and SHALL include the failure cause
