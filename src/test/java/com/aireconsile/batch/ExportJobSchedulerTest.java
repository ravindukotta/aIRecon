package com.aireconsile.batch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.explore.JobExplorer;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExportJobSchedulerTest {

    @Mock
    private JobLauncher jobLauncher;

    @Mock
    private Job exportJob;

    @Mock
    private JobExplorer jobExplorer;

    @Test
    void secondOverlappingInvocationIsSkipped() throws Exception {
        ExportJobScheduler scheduler = new ExportJobScheduler(jobLauncher, exportJob, jobExplorer);

        when(jobExplorer.findRunningJobExecutions(ExportJobScheduler.JOB_NAME))
                .thenReturn(Set.of())
                .thenReturn(Set.of(mock(JobExecution.class)));
        when(jobLauncher.run(any(), any())).thenReturn(mock(JobExecution.class));

        scheduler.launchExportJob();
        scheduler.launchExportJob();

        verify(jobLauncher, times(1)).run(any(), any());
    }
}
