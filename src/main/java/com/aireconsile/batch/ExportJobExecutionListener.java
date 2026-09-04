package com.aireconsile.batch;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ExportJobExecutionListener implements JobExecutionListener {

    @Override
    public void beforeJob(JobExecution jobExecution) {
        MDC.put("jobName", jobExecution.getJobInstance().getJobName());
        MDC.put("jobExecutionId", String.valueOf(jobExecution.getId()));
        log.info("Job started");
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        log.info("Job finished with status={}", jobExecution.getStatus());
        MDC.remove("jobName");
        MDC.remove("jobExecutionId");
    }
}
