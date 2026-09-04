package com.aireconsile.batch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.step.StepExecution;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiJobCompletionReportListenerTest {

    @TempDir
    Path reportOutputDir;

    private JobExecution newJobExecution(long executionId, long readCount, long writeCount) {
        JobInstance jobInstance = new JobInstance(1L, "exportJob");
        JobExecution jobExecution = new JobExecution(executionId, jobInstance, new JobParameters());
        jobExecution.setStartTime(LocalDateTime.now().minusSeconds(1));
        jobExecution.setEndTime(LocalDateTime.now());
        jobExecution.setStatus(BatchStatus.COMPLETED);
        jobExecution.setExitStatus(ExitStatus.COMPLETED);

        StepExecution stepExecution = new StepExecution("exportStep", jobExecution);
        stepExecution.setReadCount(readCount);
        stepExecution.setWriteCount(writeCount);
        jobExecution.addStepExecution(stepExecution);

        return jobExecution;
    }

    @Test
    void reportFileReferencesActualReadAndWriteCounts() throws Exception {
        ChatClient chatClient = mock(ChatClient.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
        when(chatClient.prompt(anyString()).call().content()).thenReturn("The run completed cleanly.");

        AiJobCompletionReportListener listener =
                new AiJobCompletionReportListener(chatClient, reportOutputDir.toString());
        listener.createOutputDir();

        JobExecution jobExecution = newJobExecution(42L, 7L, 7L);

        listener.afterJob(jobExecution);

        Path reportFile = reportOutputDir.resolve("report-42.md");
        assertThat(reportFile).exists();
        String content = Files.readString(reportFile);
        assertThat(content).contains("read=7", "write=7", "The run completed cleanly.");
    }

    @Test
    void chatClientFailureDoesNotAffectJobStatusAndWritesFallbackReport() throws Exception {
        ChatClient chatClient = mock(ChatClient.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
        when(chatClient.prompt(anyString()).call().content())
                .thenThrow(new RuntimeException("AI service unavailable"));

        AiJobCompletionReportListener listener =
                new AiJobCompletionReportListener(chatClient, reportOutputDir.toString());
        listener.createOutputDir();

        JobExecution jobExecution = newJobExecution(43L, 3L, 3L);
        jobExecution.setStatus(BatchStatus.COMPLETED);
        jobExecution.setExitStatus(ExitStatus.COMPLETED);

        listener.afterJob(jobExecution);

        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(jobExecution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);

        Path reportFile = reportOutputDir.resolve("report-43.md");
        assertThat(reportFile).exists();
        String content = Files.readString(reportFile);
        assertThat(content).contains("AI analysis unavailable", "AI service unavailable");
    }
}
