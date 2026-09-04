package com.aireconsile.batch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * On job completion (COMPLETED or FAILED), asks a Spring AI {@link ChatClient} for a natural-language
 * analysis of the run and writes it, alongside the raw metrics, to a per-execution report file. Best
 * effort: any failure here is logged and never propagates out of {@link #afterJob}, so it cannot alter
 * the job's own recorded status/exit code.
 */
@Slf4j
@Component
public class AiJobCompletionReportListener implements JobExecutionListener {

    private final ChatClient chatClient;
    private final String reportOutputDir;

    public AiJobCompletionReportListener(ChatClient chatClient,
                                          @Value("${batch.report.output-dir}") String reportOutputDir) {
        this.chatClient = chatClient;
        this.reportOutputDir = reportOutputDir;
    }

    @PostConstruct
    void createOutputDir() throws IOException {
        Files.createDirectories(Path.of(reportOutputDir));
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        String metrics = buildMetricsSummary(jobExecution);
        try {
            String analysis = chatClient.prompt(buildPrompt(metrics)).call().content();
            writeReport(jobExecution.getId(), metrics, analysis);
            log.info("AI job completion report generated for jobExecutionId={}: {}",
                    jobExecution.getId(), summarize(analysis));
        } catch (Exception e) {
            log.warn("AI job completion report generation failed for jobExecutionId={}",
                    jobExecution.getId(), e);
            writeFallbackReport(jobExecution.getId(), metrics, e);
        }
    }

    private String buildPrompt(String metrics) {
        return """
                You are assisting an operator of a Spring Batch CSV export job. Summarize the following \
                job execution in 2-4 plain-language sentences, calling out anomalies (failures, skips, \
                unusually low/zero read or write counts) if present. Do not repeat the raw data verbatim.

                %s
                """.formatted(metrics);
    }

    private String buildMetricsSummary(JobExecution jobExecution) {
        StringBuilder sb = new StringBuilder();
        sb.append("Job: ").append(jobExecution.getJobInstance().getJobName()).append('\n');
        sb.append("Execution ID: ").append(jobExecution.getId()).append('\n');
        sb.append("Status: ").append(jobExecution.getStatus()).append('\n');
        sb.append("Exit status: ").append(jobExecution.getExitStatus().getExitCode()).append('\n');

        LocalDateTime start = jobExecution.getStartTime();
        LocalDateTime end = jobExecution.getEndTime();
        if (start != null && end != null) {
            sb.append("Duration: ").append(Duration.between(start, end).toMillis()).append("ms\n");
        }

        for (StepExecution step : jobExecution.getStepExecutions()) {
            sb.append("Step '").append(step.getStepName()).append("': read=").append(step.getReadCount())
                    .append(", write=").append(step.getWriteCount())
                    .append(", skip=").append(step.getSkipCount())
                    .append(", commit=").append(step.getCommitCount())
                    .append('\n');
        }

        List<Throwable> failures = jobExecution.getAllFailureExceptions();
        if (!failures.isEmpty()) {
            sb.append("Failures:\n");
            sb.append(failures.stream()
                    .map(t -> "- " + t.getClass().getSimpleName() + ": " + t.getMessage())
                    .collect(Collectors.joining("\n")));
            sb.append('\n');
        }

        return sb.toString();
    }

    private void writeReport(long jobExecutionId, String metrics, String analysis) {
        String content = """
                # Job Completion Report

                ## Metrics
                %s
                ## AI Analysis
                %s
                """.formatted(metrics, analysis);
        writeReportFile(jobExecutionId, content);
    }

    private void writeFallbackReport(long jobExecutionId, String metrics, Exception failure) {
        String content = """
                # Job Completion Report

                ## Metrics
                %s
                ## AI Analysis
                AI analysis unavailable: %s: %s
                """.formatted(metrics, failure.getClass().getSimpleName(), failure.getMessage());
        writeReportFile(jobExecutionId, content);
    }

    private void writeReportFile(long jobExecutionId, String content) {
        try {
            Path reportFile = Path.of(reportOutputDir, "report-" + jobExecutionId + ".md");
            Files.writeString(reportFile, content);
        } catch (IOException e) {
            log.warn("Failed to write job completion report file for jobExecutionId={}", jobExecutionId, e);
        }
    }

    private String summarize(String analysis) {
        String firstLine = analysis.strip().lines().findFirst().orElse("");
        return firstLine.length() > 200 ? firstLine.substring(0, 200) + "..." : firstLine;
    }
}
