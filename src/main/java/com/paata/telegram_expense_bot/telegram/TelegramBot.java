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
     * Опциональный chat id, куда бот должен отправлять ответы.
     *
     * <p>Если значение не задано, ответ отправляется в тот же чат,
     * из которого пришло сообщение.</p>
     */
    @Value("${telegram.bot.response-chat-id:}")
    private String responseChatId;

    /**
     * Опциональный id темы Telegram-группы, куда бот должен отправлять ответы.
     *
     * <p>Если значение не задано, ответ отправляется в ту же тему,
     * из которой пришло сообщение. Для обычных чатов значение остается пустым.</p>
     */
    @Value("${telegram.bot.response-thread-id:}")
    private String responseThreadId;

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
     * <p>Бот реагирует только на текстовые сообщения из настроенного чата
     * и (при наличии настройки) только из указанной темы Telegram.
     *
     * <p>Сообщения из других чатов или тем игнорируются до вызова LLM,
     * чтобы не тратить токены и ресурсы на обработку нерелевантных сообщений.
     *
     * @param update входящее событие Telegram
     */
    @Override
    public void consume(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }

        String text = update.getMessage().getText();
        Long incomingChatId = update.getMessage().getChatId();
        Integer incomingThreadId = update.getMessage().getMessageThreadId();

        Long targetChatId = resolveTargetChatId(incomingChatId);
        Integer targetThreadId = resolveTargetThreadId(incomingThreadId);

        if (!isAllowedMessage(
                incomingChatId,
                incomingThreadId,
                targetChatId,
                targetThreadId)) {
            return;
        }
        log.info("Received message: {}", text);
        Long userId = update.getMessage()
                .getFrom()
                .getId();
        String username = resolveUsername(update.getMessage().getFrom());

        IntentResponse intentResponse = groqService.detectIntent(text);

        IntentType intent =
                intentResponse == null || intentResponse.getIntent() == null
                        ? IntentType.UNKNOWN
                        : intentResponse.getIntent();

        String response;

        switch (intent) {
            case MONTHLY_REPORT -> response = expenseService.buildMonthlyReport(text);

            case CATEGORY_REPORT -> response = expenseService.buildCategoryReport(text);

            case SAVE_EXPENSE -> response = expenseService.saveExpense(
                    text,
                    userId,
                    username
            );

            case ANALYZE -> response = expenseAnalysisService.analyzeExpenses(
                    intentResponse.getTopic(),
                    text
            );

            case UNKNOWN -> {
                return;
            }

            default -> response = """
                    Не понял запрос.
                    
                    Попробуй:
                    - "кофе 300"
                    - "отчет за месяц"
                    - "отчет по кофе за месяц"
                    """;
        }

        var sendMessageBuilder = SendMessage.builder()
                .chatId(targetChatId)
                .text(response);

        if (targetThreadId != null) {
            sendMessageBuilder.messageThreadId(targetThreadId);
        }

        SendMessage sendMessage = sendMessageBuilder.build();

        try {
            telegramClient.execute(sendMessage);
        } catch (Exception e) {
            log.error("Error while sending message", e);
        }
    }

    /**
     * Проверяет, разрешено ли обрабатывать входящее сообщение.
     *
     * <p>Если настроен целевой chat id, сообщение должно прийти именно из него.
     * Если настроен thread id, сообщение должно прийти именно из указанной темы.
     *
     * <p>Сообщения из других чатов или тем игнорируются до вызова LLM
     * и бизнес-логики, чтобы не тратить ресурсы на их обработку.
     *
     * @param incomingChatId   chat id входящего сообщения
     * @param incomingThreadId thread id входящего сообщения
     * @param targetChatId     настроенный chat id для обработки
     * @param targetThreadId   настроенный thread id для обработки
     * @return {@code true}, если сообщение разрешено к обработке
     */
    private boolean isAllowedMessage(
            Long incomingChatId,
            Integer incomingThreadId,
            Long targetChatId,
            Integer targetThreadId
    ) {
        log.info(
                "Incoming chat={}, thread={}, targetChat={}, targetThread={}",
                incomingChatId,
                incomingThreadId,
                targetChatId,
                targetThreadId
        );

        if (!incomingChatId.equals(targetChatId)) {
            return false;
        }

        return targetThreadId == null
                || targetThreadId.equals(incomingThreadId);
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

    /**
     * Определяет чат, куда нужно отправить ответ.
     *
     * <p>Если переменная окружения {@code TELEGRAM_RESPONSE_CHAT_ID} пустая,
     * используется входящий chat id и бот отвечает как обычный Telegram-бот:
     * туда же, где его спросили.</p>
     *
     * @param incomingChatId chat id входящего сообщения
     * @return chat id для отправки ответа
     */
    private Long resolveTargetChatId(Long incomingChatId) {
        if (responseChatId == null || responseChatId.isBlank()) {
            return incomingChatId;
        }

        return Long.parseLong(responseChatId.trim());
    }

    /**
     * Определяет тему Telegram-группы, куда нужно отправить ответ.
     *
     * <p>Если переменная окружения {@code TELEGRAM_RESPONSE_THREAD_ID} пустая,
     * используется thread id входящего сообщения. Если сообщение пришло из обычного
     * чата без тем, thread id будет {@code null} и в Telegram API он не передается.</p>
     *
     * @param incomingThreadId thread id входящего сообщения
     * @return thread id для отправки ответа или {@code null}
     */
    private Integer resolveTargetThreadId(Integer incomingThreadId) {
        if (responseThreadId == null || responseThreadId.isBlank()) {
            return incomingThreadId;
        }

        return Integer.parseInt(responseThreadId.trim());
    }
}
