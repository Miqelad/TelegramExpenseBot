package com.paata.telegram_expense_bot.model.dto;

import com.paata.telegram_expense_bot.model.enums.AnalysisTopic;
import com.paata.telegram_expense_bot.model.enums.IntentType;
import lombok.Data;

/**
 * DTO ответа LLM-классификатора намерений.
 *
 * <p>Используется после prompt {@code intent-classifier.txt}. Поле
 * {@code topic} заполняется для аналитических запросов и помогает сузить
 * semantic search до релевантных категорий.</p>
 */
@Data
public class IntentResponse {

    /**
     * Намерение пользователя: сохранить расход, построить общий отчет, построить
     * отчет по категории, выполнить анализ или fallback {@code UNKNOWN}.
     */
    private IntentType intent;

    /**
     * Тема анализа расходов, если intent равен {@code ANALYZE}.
     */
    private AnalysisTopic topic;
}
