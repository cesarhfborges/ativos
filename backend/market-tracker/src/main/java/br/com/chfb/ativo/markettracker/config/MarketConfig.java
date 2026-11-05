package br.com.chfb.ativo.markettracker.config;

import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

@Configuration
@EnableScheduling
public class MarketConfig {

    /** Com timeout: um provedor travado não pode segurar a coleta de todos os outros ativos. */
    @Bean
    public RestClient marketRestClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        return RestClient.builder().requestFactory(requestFactory).build();
    }
}
