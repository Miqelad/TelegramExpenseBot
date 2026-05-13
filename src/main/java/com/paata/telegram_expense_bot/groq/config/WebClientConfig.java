package com.paata.telegram_expense_bot.groq.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Конфигурация HTTP-клиента для внешних API.
 *
 * <p>Один общий {@link WebClient} используется для запросов к Groq API
 * и Jina Embeddings API.</p>
 */
@Configuration
public class WebClientConfig {

    /**
     * Создает Spring bean HTTP-клиента.
     *
     * @return настроенный экземпляр {@link WebClient}
     */
    @Bean
    public WebClient webClient() {

        return WebClient.builder().build();
    }
}
