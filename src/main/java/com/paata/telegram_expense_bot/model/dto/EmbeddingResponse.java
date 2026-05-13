package com.paata.telegram_expense_bot.model.dto;

import java.util.List;

/**
 * Ответ embedding API.
 *
 * @param data embedding results
 */
public record EmbeddingResponse(
        List<EmbeddingData> data
) {

    /**
     * Embedding result item.
     *
     * @param embedding vector embedding
     */
    public record EmbeddingData(
            List<Float> embedding
    ) {
    }
}