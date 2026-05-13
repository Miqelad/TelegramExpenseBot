package com.paata.telegram_expense_bot.telegram;

import com.paata.telegram_expense_bot.groq.service.GroqService;
import com.paata.telegram_expense_bot.model.dto.IntentResponse;
import com.paata.telegram_expense_bot.model.enums.IntentType;
import com.paata.telegram_expense_bot.service.ai.ExpenseAnalysisService;
import com.paata.telegram_expense_bot.service.expense.ExpenseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/**
 * Основной Telegram long polling бот.
 *
 * <p>Получает текстовые сообщения, определяет намерение пользователя через LLM,
 * вызывает нужный сервис и отправляет ответ обратно в чат. При сохранении расхода
 * Telegram user id и username записываются в entity, но отчеты и анализ строятся
 * по общей базе.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

    /**
     * Клиент Telegram Bot API для отправки сообщений.
     */
    private final TelegramClient telegramClient;

    /**
     * Сервис сохранения расходов и построения общих отчетов.
     */
    private final ExpenseService expenseService;

    /**
     * Сервис Groq LLM для классификации намерений пользователя.
     */
    private final GroqService groqService;

    /**
     * Сервис AI-анализа общих расходов через RAG и semantic search.
     */
    private final ExpenseAnalysisService expenseAnalysisService;

    /**
     * Токен Telegram-бота из переменной окружения {@code TELEGRAM_BOT_TOKEN}.
     */
    @Value("${telegram.bot.token}")
    private String botToken;

    /**
     * Возвращает токен бота для TelegramBots long polling starter.
     *
     * @return токен Telegram-бота
     */
    @Override
    public String getBotToken() {
        return botToken;
    }

    /**
     * Возвращает consumer, который будет обрабатывать входящие updates.
     *
     * @return текущий объект как single-thread consumer
     */
    @Override
    public LongPollingSingleThreadUpdateConsumer getUpdatesConsumer() {
        return this;
    }

    /**
     * Обрабатывает входящее событие Telegram.
     *
     * <p>Метод реагирует только на текстовые сообщения. Telegram user id
     * и username используются при сохранении расхода, а отчетные и аналитические
     * запросы намеренно не фильтруются по пользователю.</p>
     *
     * @param update входящее событие Telegram
     */
    @Override
    public void consume(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String text = update.getMessage().getText();
            Long chatId = update.getMessage().getChatId();
            log.info("Received message: {}", text);
            Long userId =
                    update.getMessage()
                            .getFrom()
                            .getId();
            String username = resolveUsername(update.getMessage().getFrom());

            IntentResponse intentResponse =
                    groqService.detectIntent(text);
            IntentType intent =
                    intentResponse == null || intentResponse.getIntent() == null
                            ? IntentType.UNKNOWN
                            : intentResponse.getIntent();
            String response;
            switch (intent) {
                case MONTHLY_REPORT -> response = expenseService
                        .buildMonthlyReport(text);
                case SAVE_EXPENSE -> response = expenseService
                        .saveExpense(text, userId, username);
                case ANALYZE -> response = expenseAnalysisService
                        .analyzeExpenses(intentResponse.getTopic(), text);
                default -> response = """
                        Не понял запрос.

                        Попробуй:
                        - "кофе 300"
                        - "отчет за месяц"
                        """;
            }
            SendMessage sendMessage = SendMessage.builder()
                    .chatId(chatId)
                    .text(response)
                    .build();
            try {
                telegramClient.execute(sendMessage);
            } catch (Exception e) {
                log.error("Error while sending message", e);
            }
        }
    }

    /**
     * Возвращает человекочитаемый никнейм автора сообщения.
     *
     * <p>Если Telegram username отсутствует, используется имя и фамилия.
     * Это позволяет сохранять автора расхода даже для пользователей без публичного
     * username.</p>
     *
     * @param user пользователь Telegram
     * @return username, имя пользователя или {@code unknown}
     */
    private String resolveUsername(User user) {
        if (user.getUserName() != null && !user.getUserName().isBlank()) {
            return user.getUserName();
        }

        String fullName = ((user.getFirstName() == null ? "" : user.getFirstName())
                + " "
                + (user.getLastName() == null ? "" : user.getLastName()))
                .trim();

        return fullName.isBlank() ? "unknown" : fullName;
    }
}
