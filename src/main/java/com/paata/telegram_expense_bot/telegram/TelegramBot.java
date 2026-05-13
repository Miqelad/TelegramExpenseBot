package com.paata.telegram_expense_bot.telegram;

import com.paata.telegram_expense_bot.groq.service.GroqService;
import com.paata.telegram_expense_bot.model.dto.IntentResponse;
import com.paata.telegram_expense_bot.model.enums.IntentType;
import com.paata.telegram_expense_bot.service.ExpenseAnalysisService;
import com.paata.telegram_expense_bot.service.ExpenseService;
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
 * Главный Telegram bot class.
 *
 * <p>
 * Получает updates от Telegram
 * и отвечает пользователю.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

    /**
     * Telegram API client.
     */
    private final TelegramClient telegramClient;
    private final ExpenseService expenseService;
    private final GroqService groqService;
    private final ExpenseAnalysisService expenseAnalysisService;

    /**
     * Bot token из application.yml
     */
    @Value("${telegram.bot.token}")
    private String botToken;

    /**
     * Возвращает bot token.
     *
     * @return telegram bot token
     */
    @Override
    public String getBotToken() {
        return botToken;
    }

    /**
     * Возвращает update consumer.
     *
     * <p>
     * В новых версиях TelegramBots
     * библиотека ожидает consumer updates.
     */
    @Override
    public LongPollingSingleThreadUpdateConsumer getUpdatesConsumer() {
        return this;
    }

    /**
     * Обработка Telegram update.
     *
     * @param update событие Telegram
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
                        .buildMonthlyReport(userId);
                case SAVE_EXPENSE -> response = expenseService
                        .saveExpense(text, userId);
                case ANALYZE ->response = expenseAnalysisService
                        .analyzeExpenses(text);
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
