package com.aireconsile.batch;

import com.aireconsile.export.ExportRecord;
import jakarta.persistence.EntityManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.database.JpaPagingItemReader;
import org.springframework.batch.infrastructure.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.batch.infrastructure.item.file.FlatFileItemWriter;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.batch.infrastructure.item.file.transform.BeanWrapperFieldExtractor;
import org.springframework.batch.infrastructure.item.file.transform.DelimitedLineAggregator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Configuration
public class ExportJobConfig {

    private static final String[] FIELD_NAMES = {"id", "customerName", "email", "amount"};

    @Value("${batch.export.output-dir}")
    private String outputDir;

    @Value("${batch.export.chunk-size:50}")
    private int chunkSize;

    @PostConstruct
    void createOutputDir() throws IOException {
        Files.createDirectories(Path.of(outputDir));
    }

    @Bean
    @StepScope
    public JpaPagingItemReader<ExportRecord> exportRecordReader(EntityManagerFactory entityManagerFactory) {
        return new JpaPagingItemReaderBuilder<ExportRecord>()
                .name("exportRecordReader")
                .entityManagerFactory(entityManagerFactory)
                .queryString("SELECT e FROM ExportRecord e ORDER BY e.id")
                .pageSize(chunkSize)
                .build();
    }

    @Bean
    @StepScope
    public FlatFileItemWriter<ExportRecord> exportRecordWriter(
            @Value("#{stepExecution.jobExecution.id}") Long jobExecutionId) {
        BeanWrapperFieldExtractor<ExportRecord> fieldExtractor = new BeanWrapperFieldExtractor<>();
        fieldExtractor.setNames(FIELD_NAMES);

        DelimitedLineAggregator<ExportRecord> lineAggregator = new DelimitedLineAggregator<>();
        lineAggregator.setDelimiter(",");
        lineAggregator.setFieldExtractor(fieldExtractor);

        // Millisecond timestamp (not just jobExecutionId) guards against filename collisions when
        // job execution ids repeat across separately-bootstrapped JobRepositories, e.g. in tests that
        // each get their own in-memory database.
        String timestamp = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").format(LocalDateTime.now());
        String fileName = "export-%s-%s.csv".formatted(jobExecutionId, timestamp);

        return new FlatFileItemWriterBuilder<ExportRecord>()
                .name("exportRecordWriter")
                .resource(new FileSystemResource(Path.of(outputDir, fileName)))
                .headerCallback(writer -> writer.write(String.join(",", FIELD_NAMES)))
                .lineAggregator(lineAggregator)
                .build();
    }

    @Bean
    public Step exportStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                            JpaPagingItemReader<ExportRecord> exportRecordReader,
                            FlatFileItemWriter<ExportRecord> exportRecordWriter) {
        return new StepBuilder("exportStep", jobRepository)
                .<ExportRecord, ExportRecord>chunk(chunkSize, transactionManager)
                .reader(exportRecordReader)
                .writer(exportRecordWriter)
                .build();
    }

    @Bean
    public Job exportJob(JobRepository jobRepository, Step exportStep,
                          ExportJobExecutionListener exportJobExecutionListener,
                          AiJobCompletionReportListener aiJobCompletionReportListener) {
        return new JobBuilder("exportJob", jobRepository)
                .listener(exportJobExecutionListener)
                .listener(aiJobCompletionReportListener)
                .start(exportStep)
                .build();
    }
}
