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
 * <p>Сервис сохраняет расходы с Telegram user id и username автора, строит отчеты
 * по всем расходам без фильтра по пользователю, генерирует embeddings для новых
 * записей и обновляет vector в PostgreSQL.</p>
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
     * AI-сервис, который извлекает один или несколько расходов из сообщения.
     */
    private final ExpenseExtractorService expenseExtractorService;

    /**
     * Сервис вычисления дат начала и конца отчетного периода.
     */
    private final ReportPeriodService reportPeriodService;

    /**
     * Формирует отчет по всем расходам за период из текстового запроса.
     *
     * @param query исходный текст запроса отчета
     * @return готовый текст отчета для отправки в Telegram
     */
    public String buildMonthlyReport(String query) {
        ReportRequest reportRequest = reportQueryService.parseReportQuery(query);
        ReportPeriod period = reportPeriodService.resolvePeriod(reportRequest);
        LocalDateTime startDate =
                period.getStartDate();
        LocalDateTime endDate =
                period.getEndDate();
        List<Expense> expenses = getExpensesForPeriod(startDate, endDate);
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
     * Сохраняет расходы из одного текстового сообщения.
     *
     * <p>Сообщение может содержать несколько расходов. Каждый расход извлекается
     * через LLM, сохраняется в базу, после чего для него генерируется embedding
     * и обновляется поле {@code embedding} в PostgreSQL.</p>
     *
     * @param text исходное сообщение
     * @param userId Telegram user id автора расходов
     * @param username Telegram username автора расходов
     * @return текстовый результат сохранения для Telegram
     */
    public String saveExpense(String text, Long userId, String username) {
        List<Expense> expenses = expenseExtractorService.extractExpenses(userId, username, text);
        for (Expense expense : expenses) {
            Expense savedExpense = expenseRepository.save(expense);
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
     * Возвращает все расходы за указанный период.
     *
     * @param startDate начало периода включительно
     * @param endDate конец периода включительно
     * @return список общих расходов за период
     */
    public List<Expense> getExpensesForPeriod(LocalDateTime startDate, LocalDateTime endDate) {
        return expenseRepository
                .findAllByCreatedAtBetween(
                        startDate,
                        endDate
                );
    }
}
