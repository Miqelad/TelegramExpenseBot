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
import org.telegram.telegrambots.meta.generics.TelegramClient;

/**
 * Основной Telegram long polling бот.
 *
 * <p>Получает текстовые сообщения пользователя, определяет intent через LLM,
 * вызывает нужный бизнес-сервис и отправляет ответ обратно в чат.</p>
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
     * Сервис сохранения расходов и построения отчетов.
     */
    private final ExpenseService expenseService;

    /**
     * Сервис Groq LLM для классификации намерений пользователя.
     */
    private final GroqService groqService;

    /**
     * Сервис AI-анализа расходов через RAG и semantic search.
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
     * <p>Метод реагирует только на текстовые сообщения. Для каждого сообщения:
     * получает user id, распознает intent, выполняет нужное действие и отправляет
     * пользователю текстовый результат.</p>
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
            IntentResponse intentResponse =
                    groqService.detectIntent(text);
            IntentType intent =
                    intentResponse == null || intentResponse.getIntent() == null
                            ? IntentType.UNKNOWN
                            : intentResponse.getIntent();
            String response;
            switch (intent) {
                case MONTHLY_REPORT -> response = expenseService
                        .buildMonthlyReport(userId, text);
                case SAVE_EXPENSE -> response = expenseService
                        .saveExpense(text, userId);
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
}
