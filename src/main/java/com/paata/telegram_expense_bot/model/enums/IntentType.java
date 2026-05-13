package com.paata.telegram_expense_bot.model.enums;

/**
 * Типы пользовательских intent.
 *
 * <p>
 * Используются для определения
 * действия пользователя.
 */
public enum IntentType {

    /**
     * Сохранение расхода.
     */
    SAVE_EXPENSE,

    /**
     * Отчет за месяц.
     */
    MONTHLY_REPORT,

    /**
     * Анализ.
     */
    ANALYZE,

    /**
     * Неизвестный intent.
     */
    UNKNOWN
}