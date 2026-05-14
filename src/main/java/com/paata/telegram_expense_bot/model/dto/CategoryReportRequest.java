package com.paata.telegram_expense_bot.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO параметров отчета по категории или теме расходов.
 *
 * <p>LLM заполняет нормализованную категорию, свободный поисковый текст по
 * описанию и параметры периода. Категория и описание могут использоваться вместе:
 * например, категория {@code DIGITAL_SERVICES} и запрос {@code chatgpt}.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CategoryReportRequest {

    /**
     * Нормализованная категория из ExpenseCategory или {@code null}, если запрос
     * лучше искать только по описанию.
     */
    private String category;

    /**
     * Свободный поисковый текст по описанию расхода.
     */
    private String descriptionQuery;

    /**
     * Количество последних месяцев для отчета.
     */
    private Integer months;

    /**
     * Название конкретного месяца на английском языке.
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

    /**
     * Возвращает параметры периода в формате обычного отчета.
     *
     * @return DTO периода для ReportPeriodService
     */
    public ReportRequest toReportRequest() {
        return new ReportRequest(
                months,
                month,
                dateFrom,
                dateTo
        );
    }
}
