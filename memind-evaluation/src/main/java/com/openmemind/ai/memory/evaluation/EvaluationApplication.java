package com.openmemind.ai.memory.evaluation;

import com.openmemind.ai.memory.evaluation.config.EvaluationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Evaluation module Spring Boot startup entry
 *
 */
@SpringBootApplication
@EnableConfigurationProperties(EvaluationProperties.class)
public class EvaluationApplication {
    public static void main(String[] args) {
        SpringApplication.run(EvaluationApplication.class, args);
    }
}
