package com.paata.telegram_expense_bot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Конфигурация общего HTTP-клиента для внешних API.
 *
 * <p>Один {@link WebClient} используется для запросов к Gemini Chat API,
 * Gemini Embeddings API и другим внешним HTTP-сервисам приложения.</p>
 */
@Configuration
public class WebClientConfig {

    /**
     * Создает общий {@link WebClient} bean без базового URL.
     *
     * <p>Сервисы задают полный URL самостоятельно, потому что используют разные
     * endpoint'ы Gemini API.</p>
     *
     * @return настроенный экземпляр {@link WebClient}
     */
    @Bean
    public WebClient webClient() {
        return WebClient.builder().build();
    }
}
