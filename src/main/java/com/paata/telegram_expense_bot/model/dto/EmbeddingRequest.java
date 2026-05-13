package com.paata.telegram_expense_bot.model.dto;

import java.util.List;

/**
 * DTO запроса к Jina Embeddings API.
 *
 * @param model название embedding-модели
 * @param input список текстов, для которых нужно построить embeddings
 */
public record EmbeddingRequest(
        String model,
        List<String> input
) {
}
