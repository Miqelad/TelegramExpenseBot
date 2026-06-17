package com.paata.telegram_expense_bot.service.expense;

import com.paata.telegram_expense_bot.groq.service.GroqService;
import com.paata.telegram_expense_bot.model.dto.CategoryReportRequest;
import com.paata.telegram_expense_bot.model.dto.ReportPeriod;
import com.paata.telegram_expense_bot.model.dto.ReportRequest;
import com.paata.telegram_expense_bot.model.entity.Expense;
import com.paata.telegram_expense_bot.model.enums.ExpenseCategory;
import com.paata.telegram_expense_bot.prompt.PromptLoader;
import com.paata.telegram_expense_bot.repository.ExpenseRepository;
import com.paata.telegram_expense_bot.service.EmbeddingService;
import com.paata.telegram_expense_bot.service.ai.CategoryReportQueryService;
import com.paata.telegram_expense_bot.service.ai.ReportQueryService;
import com.paata.telegram_expense_bot.service.report.ReportPeriodService;
import com.paata.telegram_expense_bot.util.VectorUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
     * AI-сервис, который превращает текст запроса категорийного отчета в параметры.
     */
    private final CategoryReportQueryService categoryReportQueryService;

    /**
     * Сервис обращения к Groq LLM для короткой сводки отчета.
     */
    private final GroqService groqService;

    /**
     * Загрузчик prompt-файлов.
     */
    private final PromptLoader promptLoader;

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
                                        entry.getKey().getDisplayName(),
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
     * Формирует отчет по конкретной категории или теме расходов.
     *
     * <p>LLM разбирает категорию, период и свободный текст для поиска по
     * описанию. Отчет строится по общей базе расходов без фильтра по автору.</p>
     *
     * @param query исходный текст запроса отчета
     * @return готовый текст отчета для отправки в Telegram
     */
    public String buildCategoryReport(String query) {
        CategoryReportRequest categoryReportRequest =
                categoryReportQueryService.parseCategoryReportQuery(query);

        if (categoryReportRequest == null) {
            return """
                    Не смог разобрать параметры отчета.

                    Попробуй:
                    - "отчет по кофе за месяц"
                    - "расходы на такси за апрель"
                    """;
        }

        ExpenseCategory category =
                parseCategory(categoryReportRequest.getCategory());
        String descriptionQuery =
                normalizeDescriptionQuery(
                        categoryReportRequest.getDescriptionQuery()
                );

        if (category == null && descriptionQuery == null) {
            return """
                    Не понял, по какой категории или теме сделать отчет.

                    Попробуй:
                    - "отчет по кофе за месяц"
                    - "расходы на такси за апрель"
                    - "покажи chatgpt за последние 3 месяца"
                    """;
        }

        ReportPeriod period =
                reportPeriodService.resolvePeriod(
                        categoryReportRequest.toReportRequest()
                );
        LocalDateTime startDate =
                period.getStartDate();
        LocalDateTime endDate =
                period.getEndDate();

        List<Expense> expenses =
                getExpensesForPeriod(
                        startDate,
                        endDate
                )
                        .stream()
                        .filter(expense -> matchesCategory(expense, category))
                        .filter(expense -> matchesDescription(expense, descriptionQuery))
                        .sorted(Comparator.comparing(
                                Expense::getCreatedAt,
                                Comparator.nullsLast(Comparator.naturalOrder())
                        ))
                        .toList();

        String reportTitle =
                buildCategoryReportTitle(
                        category,
                        descriptionQuery
                );

        if (expenses.isEmpty()) {
            return """
                    Отчет по расходам: %s

                    Период:
                    с %s по %s

                    Расходов нет.
                    """.formatted(
                    reportTitle,
                    startDate.toLocalDate(),
                    endDate.toLocalDate()
            );
        }

        BigDecimal totalAmount =
                expenses.stream()
                        .map(Expense::getAmount)
                        .reduce(
                                BigDecimal.ZERO,
                                BigDecimal::add
                        );

        Map<String, BigDecimal> descriptionTotals =
                expenses.stream()
                        .collect(Collectors.groupingBy(
                                expense -> normalizeDescription(
                                        expense.getDescription()
                                ),
                                LinkedHashMap::new,
                                Collectors.reducing(
                                        BigDecimal.ZERO,
                                        Expense::getAmount,
                                        BigDecimal::add
                                )
                        ));

        String descriptionsText =
                descriptionTotals.entrySet()
                        .stream()
                        .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                        .map(entry ->
                                "%s - %s RUB".formatted(
                                        entry.getKey(),
                                        entry.getValue()
                                )
                        )
                        .collect(Collectors.joining("\n"));

        String operationsText =
                expenses.stream()
                        .limit(30)
                        .map(this::formatExpenseLine)
                        .collect(Collectors.joining("\n"));

        String operationsSuffix =
                expenses.size() > 30
                        ? "\n...и еще %s".formatted(expenses.size() - 30)
                        : "";

        String reportData =
                """
                Тема: %s
                Период: с %s по %s
                Количество расходов: %s
                Итого: %s RUB

                По описаниям:
                %s

                Операции:
                %s%s
                """.formatted(
                        reportTitle,
                        startDate.toLocalDate(),
                        endDate.toLocalDate(),
                        expenses.size(),
                        totalAmount,
                        descriptionsText,
                        operationsText,
                        operationsSuffix
                );

        String aiSummary =
                buildCategoryReportSummary(
                        query,
                        reportData
                );

        return """
                Отчет по расходам: %s

                Период:
                с %s по %s

                По описаниям:
                %s

                Операции:
                %s%s

                -------------------
                Количество: %s
                Итого: %s RUB

                AI-сводка:
                %s
                """.formatted(
                reportTitle,
                startDate.toLocalDate(),
                endDate.toLocalDate(),
                descriptionsText,
                operationsText,
                operationsSuffix,
                expenses.size(),
                totalAmount,
                aiSummary
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

    /**
     * Безопасно преобразует строковую категорию из LLM в enum.
     */
    private ExpenseCategory parseCategory(String category) {
        if (category == null || category.isBlank()) {
            return null;
        }

        try {
            return ExpenseCategory.valueOf(
                    category.trim().toUpperCase(Locale.ROOT)
            );
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Нормализует свободный поисковый текст по описанию.
     */
    private String normalizeDescriptionQuery(String descriptionQuery) {
        if (descriptionQuery == null || descriptionQuery.isBlank()) {
            return null;
        }

        return descriptionQuery
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    /**
     * Проверяет совпадение расхода с категорией отчета.
     */
    private boolean matchesCategory(Expense expense, ExpenseCategory category) {
        return category == null || expense.getCategory() == category;
    }

    /**
     * Проверяет совпадение расхода со свободным текстом в описании.
     */
    private boolean matchesDescription(Expense expense, String descriptionQuery) {
        if (descriptionQuery == null) {
            return true;
        }

        String description =
                expense.getDescription() == null
                        ? ""
                        : expense.getDescription().toLowerCase(Locale.ROOT);

        return description.contains(descriptionQuery);
    }

    /**
     * Возвращает нормализованное описание для группировки.
     */
    private String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return "без описания";
        }

        return description
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    /**
     * Форматирует одну операцию отчета.
     */
    private String formatExpenseLine(Expense expense) {
        String createdAt =
                expense.getCreatedAt() == null
                        ? "без даты"
                        : expense.getCreatedAt().format(
                                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                        );

        return "%s - %s - %s RUB".formatted(
                createdAt,
                expense.getDescription(),
                expense.getAmount()
        );
    }

    /**
     * Формирует человекочитаемое название отчета.
     */
    private String buildCategoryReportTitle(
            ExpenseCategory category,
            String descriptionQuery
    ) {
        if (category != null && descriptionQuery != null) {
            return "%s / %s".formatted(
                    category,
                    descriptionQuery
            );
        }

        if (category != null) {
            return category.toString();
        }

        return descriptionQuery;
    }

    /**
     * Строит короткую AI-сводку по готовым данным отчета.
     */
    private String buildCategoryReportSummary(
            String query,
            String reportData
    ) {
        String systemPrompt =
                promptLoader.loadPrompt(
                        "prompts/category-report-summary.txt"
                )
                        .formatted(reportData, query);

        return groqService.ask(
                systemPrompt,
                query
        );
    }
}
