package com.paata.telegram_expense_bot.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO параметров отчета, которые извлекаются из текста через LLM.
 *
 * <p>Модель может заполнить одно из полей: количество месяцев, конкретный
 * месяц или диапазон дат. Дальше эти данные превращаются в {@link ReportPeriod}.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReportRequest {

    /**
     * Количество последних месяцев для отчета.
     *
     * <p>Пример пользовательской фразы: "отчет за 3 месяца".</p>
     */
    private Integer months;

    /**
     * Название конкретного месяца на английском языке.
     *
     * <p>Пример значения: {@code APRIL}.</p>
     */
    private String month;

    /**
     * Начальная дата периода в формате {@code yyyy-MM-dd}.
     */
    private String dateFrom;

    /**
     * Конечная дата периода в формате {@code yyyy-MM-dd}.
     */
    private String dateTo;
}
