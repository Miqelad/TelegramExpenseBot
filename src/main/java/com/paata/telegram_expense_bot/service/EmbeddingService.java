package com.paata.telegram_expense_bot.service;

import com.paata.telegram_expense_bot.model.dto.EmbeddingRequest;
import com.paata.telegram_expense_bot.model.dto.EmbeddingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

/**
 * Сервис генерации vector embeddings через Jina AI.
 *
 * <p>Преобразует текст в числовой вектор, который затем сохраняется в
 * PostgreSQL pgvector и используется для semantic search и RAG.</p>
 */
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    /**
     * HTTP-клиент для вызова Jina Embeddings API.
     */
    private final WebClient webClient;

    /**
     * API-ключ Jina AI. Значение приходит из переменной окружения {@code JINA_API_KEY}.
     */
    @Value("${jina.api-key}")
    private String apiKey;

    /**
     * Генерирует embedding для одного текста.
     *
     * @param text текст, который нужно представить вектором
     * @return embedding-вектор модели {@code jina-embeddings-v3}
     */
    public List<Float> generateEmbedding(String text) {

        EmbeddingRequest request =
                new EmbeddingRequest(
                        "jina-embeddings-v3",
                        List.of(text)
                );

        EmbeddingResponse response =
                webClient.post()
                        .uri("https://api.jina.ai/v1/embeddings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .bodyValue(request)
                        .retrieve()
                        .bodyToMono(EmbeddingResponse.class)
                        .block();

        return response.data()
                .getFirst()
                .embedding();
    }
}
