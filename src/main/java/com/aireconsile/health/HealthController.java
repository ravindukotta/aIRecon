package com.aireconsile.health;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/health")
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/live")
    public HealthResponse liveness() {
        return new HealthResponse("UP");
    }

    @GetMapping("/ready")
    public ResponseEntity<HealthResponse> readiness() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return ResponseEntity.ok(new HealthResponse("UP"));
        } catch (DataAccessException exception) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new HealthResponse("DOWN"));
        }
    }

    public record HealthResponse(String status) {
    }
}
