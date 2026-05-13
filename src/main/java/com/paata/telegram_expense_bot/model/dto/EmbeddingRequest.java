package com.paata.telegram_expense_bot.model.dto;

import java.util.List;

/**
 * Запрос на генерацию embeddings.
 *
 * @param model embedding model name
 * @param input input texts
 */
public record EmbeddingRequest(
        String model,
        List<String> input
) {
}