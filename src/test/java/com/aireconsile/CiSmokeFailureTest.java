package com.aireconsile;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// Temporary: deliberately failing test to verify GitHub Actions CI reports failures. Remove after verification.
class CiSmokeFailureTest {

    //@Ignore
    //@Test
    void deliberatelyFails() {
        assertThat(1 + 1).isEqualTo(3);
    }
}
