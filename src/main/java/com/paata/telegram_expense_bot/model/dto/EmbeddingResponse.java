package com.paata.telegram_expense_bot.model.dto;

import java.util.List;

/**
 * DTO ответа Jina Embeddings API.
 *
 * @param data список результатов генерации embeddings
 */
public record EmbeddingResponse(
        List<EmbeddingData> data
) {

    /**
     * Один элемент ответа с embedding-вектором.
     *
     * @param embedding числовой vector embedding текста
     */
    public record EmbeddingData(
            List<Float> embedding
    ) {
    }
}
