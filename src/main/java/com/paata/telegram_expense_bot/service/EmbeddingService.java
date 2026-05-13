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
 * Сервис генерации vector embeddings.
 *
 * <p>
 * Преобразует пользовательский текст
 * в vector representation.
 *
 * <p>
 * Embeddings используются для:
 * <ul>
 *     <li>semantic search</li>
 *     <li>vector similarity</li>
 *     <li>RAG pipeline</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final WebClient webClient;

    @Value("${jina.api-key}")
    private String apiKey;

    /**
     * Генерирует embedding для текста.
     *
     * @param text source text
     * @return vector embedding
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