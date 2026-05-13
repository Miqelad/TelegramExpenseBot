package com.paata.telegram_expense_bot.groq.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Конфигурация WebClient.
 */
@Configuration
public class WebClientConfig {

    /**
     * Создает WebClient bean.
     *
     * @return WebClient
     */
    @Bean
    public WebClient webClient() {

        return WebClient.builder().build();
    }
}