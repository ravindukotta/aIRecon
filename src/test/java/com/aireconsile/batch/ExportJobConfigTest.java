package com.aireconsile.batch;

import com.aireconsile.export.ExportRecord;
import com.aireconsile.export.ExportRecordRepository;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@SpringBatchTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ExportJobConfigTest {

    @TempDir
    static Path outputDir;

    @DynamicPropertySource
    static void overrideOutputDir(DynamicPropertyRegistry registry) {
        registry.add("batch.export.output-dir", outputDir::toString);
    }

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private ExportRecordRepository exportRecordRepository;

    // Avoids a real network call to OpenAI from AiJobCompletionReportListener.afterJob on every job
    // run in this test class; that listener's own behavior is covered by AiJobCompletionReportListenerTest.
    @MockitoBean
    private ChatClient chatClient;

    @Test
    @Order(1)
    void seededRecordsProduceCsvWithHeaderAndOneRowPerRecord() throws Exception {
        exportRecordRepository.deleteAllInBatch();
        exportRecordRepository.saveAll(List.of(
                new ExportRecord(null, "Test User One", "one@example.com", new BigDecimal("10.00")),
                new ExportRecord(null, "Test User Two", "two@example.com", new BigDecimal("20.00")),
                new ExportRecord(null, "Test User Three", "three@example.com", new BigDecimal("30.00"))));
        long recordCount = exportRecordRepository.count();

        Set<Path> filesBefore = listOutputDir();
        JobExecution jobExecution = jobLauncherTestUtils.launchJob(
                jobLauncherTestUtils.getUniqueJobParameters());

        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        Path csvFile = findNewCsv(filesBefore);
        List<String> lines = Files.readAllLines(csvFile);
        assertThat(lines).hasSize((int) recordCount + 1);
        assertThat(lines.get(0)).isEqualTo("id,customerName,email,amount");
    }

    @Test
    @Order(2)
    void zeroRecordsProduceHeaderOnlyCsv() throws Exception {
        exportRecordRepository.deleteAllInBatch();

        Set<Path> filesBefore = listOutputDir();
        JobExecution jobExecution = jobLauncherTestUtils.launchJob(
                jobLauncherTestUtils.getUniqueJobParameters());

        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        Path csvFile = findNewCsv(filesBefore);
        List<String> lines = Files.readAllLines(csvFile);
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0)).isEqualTo("id,customerName,email,amount");
    }

    private Set<Path> listOutputDir() throws IOException {
        try (Stream<Path> files = Files.list(outputDir)) {
            return files.collect(Collectors.toSet());
        }
    }

    /**
     * Job execution ids are not guaranteed unique across test methods in this suite (each method may
     * get its own Spring context/JobRepository), so the export filename (which embeds the execution id)
     * cannot reliably identify "this test's" file by itself. Diffing the directory before/after the
     * launch identifies the file this specific launch produced, regardless of id reuse.
     */
    private Path findNewCsv(Set<Path> filesBefore) throws IOException {
        try (Stream<Path> files = Files.list(outputDir)) {
            return files.filter(p -> !filesBefore.contains(p))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No new export CSV file was generated"));
        }
    }
}
