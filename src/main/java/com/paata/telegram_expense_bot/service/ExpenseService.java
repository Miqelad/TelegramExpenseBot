package com.paata.telegram_expense_bot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paata.telegram_expense_bot.groq.service.GroqService;
import com.paata.telegram_expense_bot.model.dto.ExpenseRequest;
import com.paata.telegram_expense_bot.model.entity.Expense;
import com.paata.telegram_expense_bot.model.enums.ExpenseCategory;
import com.paata.telegram_expense_bot.repository.ExpenseRepository;
import com.paata.telegram_expense_bot.util.VectorUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final GroqService groqService;
    private final ExpenseRepository expenseRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EmbeddingService embeddingService;

    /**
     * Формирует отчет по расходам пользователя
     * за текущий месяц.
     *
     * @param userId Telegram user id
     * @return текстовый отчет по расходам
     */
    public String buildMonthlyReport(Long userId) {

        List<Expense> expenses =
                getExpensesForCurrentMonth(userId);

        if (expenses.isEmpty()) {
            return "Расходов за текущий месяц нет.";
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
                                entry.getKey()
                                        + " — "
                                        + entry.getValue()
                                        + "₽"
                        )
                        .collect(Collectors.joining("\n"));

        BigDecimal totalAmount =
                expenses.stream()
                        .map(Expense::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        return """
            Расходы за текущий месяц:
            
            %s
            
            -------------------
            Итого: %s₽
            """.formatted(
                categoriesText,
                totalAmount
        );
    }

    /**
     * Сохраняет расход пользователя.
     *
     * @param text   сообщение пользователя
     * @param userId Telegram user id
     * @return результат сохранения
     */
    public String saveExpense(String text, Long userId) {

        Expense expense = extractExpense(userId, text);

        expense.setUserId(userId);
        expense.setCreatedAt(LocalDateTime.now());

        Expense savedExpense =
                expenseRepository.save(expense);

        String embeddingText =
                (expense.getDescription() == null
                        ? ""
                        : expense.getDescription())
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

        return """
            Расход сохранен:
            
            Категория: %s
            Сумма: %s₽
            Описание: %s
            """.formatted(
                expense.getCategory(),
                expense.getAmount(),
                expense.getDescription()
        );
    }

    /**
     * Возвращает расходы пользователя за текущий месяц.
     *
     * @param userId идентификатор пользователя Telegram
     * @return список расходов за текущий месяц
     */
    public List<Expense> getExpensesForCurrentMonth(Long userId) {
        LocalDateTime startOfMonth =
                LocalDate.now()
                        .withDayOfMonth(1)
                        .atStartOfDay();
        LocalDateTime now =
                LocalDateTime.now();

        return expenseRepository
                .findAllByUserIdAndCreatedAtBetween(
                        userId,
                        startOfMonth,
                        now
                );
    }

    /**
     * Извлекает расход из текста пользователя.
     *
     * @param userId Telegram user id
     * @param text   сообщение пользователя
     * @return объект расхода
     */
    public Expense extractExpense(Long userId, String text) {

        try {

            String response =
                    groqService.ask(text);

            ExpenseRequest dto =
                    objectMapper.readValue(
                            response,
                            ExpenseRequest.class
                    );

            return Expense.builder()
                    .userId(userId)
                    .amount(dto.getAmount())
                    .category(dto.getCategory())
                    .description(dto.getDescription())
                    .createdAt(LocalDateTime.now())
                    .build();

        } catch (Exception e) {

            throw new RuntimeException(
                    "Ошибка парсинга expense",
                    e
            );
        }
    }
}