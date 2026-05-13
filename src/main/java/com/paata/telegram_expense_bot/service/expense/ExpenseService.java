package com.paata.telegram_expense_bot.service.expense;

import com.paata.telegram_expense_bot.model.dto.ReportPeriod;
import com.paata.telegram_expense_bot.model.dto.ReportRequest;
import com.paata.telegram_expense_bot.model.entity.Expense;
import com.paata.telegram_expense_bot.model.enums.ExpenseCategory;
import com.paata.telegram_expense_bot.repository.ExpenseRepository;
import com.paata.telegram_expense_bot.service.EmbeddingService;
import com.paata.telegram_expense_bot.service.ai.ReportQueryService;
import com.paata.telegram_expense_bot.service.report.ReportPeriodService;
import com.paata.telegram_expense_bot.util.VectorUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Основной сервис бизнес-логики расходов.
 *
 * <p>Сервис сохраняет расходы пользователя, строит отчеты за выбранный период,
 * генерирует embeddings для новых расходов и записывает vector в PostgreSQL.</p>
 */
@Service
@RequiredArgsConstructor
public class ExpenseService {

    /**
     * Репозиторий для чтения и записи расходов.
     */
    private final ExpenseRepository expenseRepository;

    /**
     * Сервис генерации embedding-векторов через Jina AI.
     */
    private final EmbeddingService embeddingService;

    /**
     * AI-сервис, который превращает текст запроса отчета в структурированные параметры.
     */
    private final ReportQueryService reportQueryService;

    /**
     * AI-сервис, который извлекает один или несколько расходов из сообщения пользователя.
     */
    private final ExpenseExtractorService expenseExtractorService;

    /**
     * Сервис вычисления дат начала и конца отчетного периода.
     */
    private final ReportPeriodService reportPeriodService;

    /**
     * Формирует отчет по расходам пользователя за период из текстового запроса.
     *
     * <p>Пользователь может попросить отчет за текущий месяц, за N месяцев,
     * за конкретный месяц или за диапазон дат. Запрос сначала парсится через LLM,
     * после чего расходы агрегируются по категориям.</p>
     *
     * @param userId Telegram user id пользователя
     * @param query исходный текст запроса отчета
     * @return готовый текст отчета для отправки в Telegram
     */
    public String buildMonthlyReport(Long userId, String query) {
        ReportRequest reportRequest = reportQueryService.parseReportQuery(query);
        ReportPeriod period = reportPeriodService.resolvePeriod(reportRequest);
        LocalDateTime startDate =
                period.getStartDate();

        LocalDateTime endDate =
                period.getEndDate();

        List<Expense> expenses =
                getExpensesForPeriod(
                        userId,
                        startDate,
                        endDate
                );

        if (expenses.isEmpty()) {
            return """
                Отчет по расходам

                Период:
                с %s по %s

                Расходов нет.
                """.formatted(
                    startDate.toLocalDate(),
                    endDate.toLocalDate()
            );
        }

        Map<ExpenseCategory, BigDecimal> categoryTotals =
                expenses.stream()
                        .collect(Collectors.groupingBy(
                                Expense::getCategory,
                                Collectors.reducing(
                                        BigDecimal.ZERO,
                                        Expense::getAmount,
                                        BigDecimal::add
                                )
                        ));

        String categoriesText =
                categoryTotals.entrySet()
                        .stream()
                        .map(entry ->
                                """
                                %s - %s RUB
                                """.formatted(
                                        entry.getKey(),
                                        entry.getValue()
                                )
                        )
                        .collect(Collectors.joining("\n"));

        BigDecimal totalAmount =
                expenses.stream()
                        .map(Expense::getAmount)
                        .reduce(
                                BigDecimal.ZERO,
                                BigDecimal::add
                        );

        String periodText =
                """
                с %s по %s
                """.formatted(
                        startDate.toLocalDate(),
                        endDate.toLocalDate()
                );

        return """
            Отчет по расходам

            Период:
            %s

            %s

            -------------------
            Итого: %s RUB
            """.formatted(
                periodText,
                categoriesText,
                totalAmount
        );
    }

    /**
     * Сохраняет расходы пользователя из одного текстового сообщения.
     *
     * <p>Сообщение может содержать несколько расходов. Каждый расход извлекается
     * через LLM, сохраняется в базу, после чего для него генерируется embedding
     * и обновляется поле {@code embedding} в PostgreSQL.</p>
     *
     * @param text исходное сообщение пользователя
     * @param userId Telegram user id пользователя
     * @return текстовый результат сохранения для Telegram
     */
    public String saveExpense(String text, Long userId) {
        List<Expense> expenses = expenseExtractorService.extractExpenses(userId, text);
        for (Expense expense : expenses) {
            Expense savedExpense = expenseRepository.save(expense);
            expense.setUserId(userId);
            expense.setCreatedAt(LocalDateTime.now());
            String embeddingText =
                    expense.getDescription()
                            + " "
                            + expense.getCategory();

            List<Float> embedding =
                    embeddingService.generateEmbedding(
                            embeddingText
                    );

            String pgVector =
                    VectorUtils.toPgVector(embedding);

            expenseRepository.updateEmbedding(
                    savedExpense.getUuid(),
                    pgVector
            );
        }
        String savedExpenses =
                expenses.stream()
                        .map(expense ->
                                """
                                        %s - %s RUB
                                        """.formatted(
                                        expense.getDescription(),
                                        expense.getAmount()
                                )
                        )
                        .collect(Collectors.joining("\n"));
        return """
                Расходы сохранены:

                %s
                """.formatted(savedExpenses);
    }

    /**
     * Возвращает расходы пользователя за указанный период.
     *
     * @param userId Telegram user id пользователя
     * @param startDate начало периода включительно
     * @param endDate конец периода включительно
     * @return список расходов пользователя за период
     */
    public List<Expense> getExpensesForPeriod(Long userId, LocalDateTime startDate, LocalDateTime endDate) {
        return expenseRepository
                .findAllByUserIdAndCreatedAtBetween(
                        userId,
                        startDate,
                        endDate
                );
    }
}
