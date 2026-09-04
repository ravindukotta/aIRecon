package com.aireconsile.batch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.explore.JobExplorer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExportJobScheduler {

    static final String JOB_NAME = "exportJob";

    private final JobLauncher jobLauncher;
    private final Job exportJob;
    private final JobExplorer jobExplorer;

    @Scheduled(cron = "${batch.export.cron}")
    public void launchExportJob() {
        Set<JobExecution> runningExecutions = jobExplorer.findRunningJobExecutions(JOB_NAME);
        if (!runningExecutions.isEmpty()) {
            log.warn("Skipping scheduled launch of {}: a previous execution is still running (executionIds={})",
                    JOB_NAME, runningExecutions.stream().map(JobExecution::getId).toList());
            return;
        }

        JobParameters jobParameters = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        try {
            JobExecution jobExecution = jobLauncher.run(exportJob, jobParameters);
            log.info("Launched {} with executionId={}, status={}", JOB_NAME, jobExecution.getId(), jobExecution.getStatus());
        } catch (Exception e) {
            log.error("Failed to launch {}", JOB_NAME, e);
        }
    }
}
