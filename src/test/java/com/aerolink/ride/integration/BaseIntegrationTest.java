package com.aerolink.ride.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

/**
 * Base class for integration tests.
 * Uses the 'integration' Spring profile which connects to
 * the already-running Docker containers (docker-compose up).
 *
 * Prerequisites: Run `docker-compose up postgres redis rabbitmq -d` first.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration")
@Sql(scripts = "/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
public abstract class BaseIntegrationTest {
}
