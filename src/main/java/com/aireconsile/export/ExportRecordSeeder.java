package com.aireconsile.export;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExportRecordSeeder implements CommandLineRunner {

    private final ExportRecordRepository exportRecordRepository;

    @Override
    public void run(String... args) {
        if (exportRecordRepository.count() > 0) {
            return;
        }
        List<ExportRecord> demoRecords = List.of(
                new ExportRecord(null, "Alice Johnson", "alice.johnson@example.com", new BigDecimal("120.50")),
                new ExportRecord(null, "Bob Smith", "bob.smith@example.com", new BigDecimal("89.99")),
                new ExportRecord(null, "Carla Diaz", "carla.diaz@example.com", new BigDecimal("450.00")),
                new ExportRecord(null, "David Lee", "david.lee@example.com", new BigDecimal("15.75")),
                new ExportRecord(null, "Emma Wilson", "emma.wilson@example.com", new BigDecimal("999.10"))
        );
        exportRecordRepository.saveAll(demoRecords);
        log.info("Seeded {} demo ExportRecord rows", demoRecords.size());
    }
}
