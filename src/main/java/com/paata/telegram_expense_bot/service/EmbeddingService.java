package com.paata.telegram_expense_bot.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Сервис генерации embedding-векторов через Gemini Embeddings API.
 *
 * <p>Преобразует текст расхода или пользовательского запроса в числовой вектор,
 * который затем сохраняется в PostgreSQL {@code pgvector} и используется для
 * semantic search/RAG. Размерность управляется настройкой
 * {@code gemini.embedding-dimensions}; по умолчанию проект использует 1024,
 * чтобы сохранить совместимость с колонкой {@code embedding vector(1024)}.</p>
 */
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private static final String GEMINI_API_BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/";

    private final WebClient webClient;

    @Value("${gemini.api-key}")
    private String apiKey;

    @Value("${gemini.embedding-model}")
    private String embeddingModel;

    @Value("${gemini.embedding-dimensions}")
    private Integer embeddingDimensions;

    @Value("${gemini.embedding-retry-max-attempts:5}")
    private int embeddingRetryMaxAttempts;

    @Value("${gemini.embedding-retry-base-delay-ms:5000}")
    private long embeddingRetryBaseDelayMs;

    @Value("${gemini.embedding-retry-max-delay-ms:60000}")
    private long embeddingRetryMaxDelayMs;

    /**
     * Генерирует embedding для одного текста.
     *
     * <p>При ответе Gemini {@code 429 Too Many Requests} метод делает повторные
     * попытки с паузой. Количество попыток и задержки задаются настройками
     * {@code gemini.embedding-retry-*}. Если API прислал заголовок
     * {@code Retry-After}, используется его значение; иначе применяется
     * возрастающая задержка.</p>
     *
     * @param text текст, который нужно представить вектором
     * @return embedding-вектор заданной размерности
     */
    public List<Float> generateEmbedding(String text) {
        int maxAttempts =
                Math.max(1, embeddingRetryMaxAttempts);

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return requestEmbedding(text);
            } catch (WebClientResponseException.TooManyRequests e) {
                if (attempt == maxAttempts) {
                    throw e;
                }

                sleepBeforeRetry(e, attempt);
            }
        }

        throw new IllegalStateException("Цикл повторных запросов Gemini embedding завершился неожиданно");
    }

    /**
     * Выполняет один HTTP-запрос к Gemini {@code embedContent}.
     *
     * @param text текст для векторизации
     * @return embedding-вектор из поля {@code embedding.values}
     */
    private List<Float> requestEmbedding(String text) {
        Map<String, Object> requestBody =
                Map.of(
                        "model", "models/" + embeddingModel,
                        "content", Map.of(
                                "parts", List.of(
                                        Map.of("text", text)
                                )
                        ),
                        "outputDimensionality", embeddingDimensions
                );

        Map response =
                webClient.post()
                        .uri(GEMINI_API_BASE_URL + embeddingModel + ":embedContent")
                        .header("x-goog-api-key", apiKey)
                        .header(HttpHeaders.CONTENT_TYPE, "application/json")
                        .bodyValue(requestBody)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .block();

        Map embedding =
                (Map) response.get("embedding");

        List values =
                (List) embedding.get("values");

        return values.stream()
                .map(value -> ((Number) value).floatValue())
                .toList();
    }

    /**
     * Ожидает перед повторной попыткой после лимита Gemini.
     *
     * @param e исключение с HTTP-ответом {@code 429}
     * @param attempt номер текущей попытки, начиная с единицы
     */
    private void sleepBeforeRetry(
            WebClientResponseException.TooManyRequests e,
            int attempt
    ) {
        try {
            Thread.sleep(resolveRetryDelay(e, attempt).toMillis());
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Ожидание повторного запроса Gemini embedding было прервано",
                    interruptedException
            );
        }
    }

    /**
     * Вычисляет задержку перед повторной попыткой.
     *
     * <p>Сначала учитывается HTTP-заголовок {@code Retry-After}. Если он
     * отсутствует или не является числом секунд, используется простая возрастающая
     * задержка с верхней границей из {@code gemini.embedding-retry-max-delay-ms}.</p>
     *
     * @param e исключение с HTTP-заголовками ответа
     * @param attempt номер попытки, начиная с единицы
     * @return длительность паузы перед следующим запросом
     */
    private Duration resolveRetryDelay(
            WebClientResponseException.TooManyRequests e,
            int attempt
    ) {
        String retryAfter =
                e.getHeaders().getFirst(HttpHeaders.RETRY_AFTER);

        if (retryAfter != null) {
            try {
                return Duration.ofSeconds(Long.parseLong(retryAfter));
            } catch (NumberFormatException ignored) {
                // Если Retry-After не подходит, ниже используем возрастающую задержку.
            }
        }

        long delayMs =
                Math.min(
                        embeddingRetryMaxDelayMs,
                        embeddingRetryBaseDelayMs * attempt
                );

        return Duration.ofMillis(delayMs);
    }
}
