package br.com.chfb.ativo.predictiveengine.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "predictive.enabled", havingValue = "true")
@EnableConfigurationProperties(PredictiveProperties.class)
public class PredictiveConfig {
}
