package com.smartquiz.config;

import com.smartquiz.ai.AIService;
import com.smartquiz.dto.MCQDTO;
import com.smartquiz.dto.SimilarityCheckResponse;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;

import java.util.Collections;

@TestConfiguration
public class TestConfig {

    /** Fresh schema for every test context: drop everything, then apply all migrations. */
    @Bean
    public FlywayMigrationStrategy cleanMigrateStrategy() {
        return flyway -> {
            flyway.clean();
            flyway.migrate();
        };
    }

    @Bean
    @Primary
    public AIService aiService() {
        AIService mock = Mockito.mock(AIService.class);

        Mockito.when(mock.generateQuestions(
                Mockito.any(), Mockito.any(), Mockito.anyString(),
                Mockito.anyInt(), Mockito.any(), Mockito.any()))
                .thenReturn(Collections.emptyList());

        Mockito.when(mock.generateQuestions(
            Mockito.any(), Mockito.any(), Mockito.anyString(),
            Mockito.anyInt(), Mockito.any(), Mockito.any(),
            Mockito.anyString(), Mockito.anyList()))
            .thenReturn(Collections.emptyList());

        Mockito.when(mock.checkSimilarity(
                Mockito.anyString(), Mockito.anyString(), Mockito.anyString(),
                Mockito.anyString(), Mockito.anyString(), Mockito.any(),
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(new SimilarityCheckResponse(false, Collections.emptyList()));

        return mock;
    }
}