package com.paata.telegram_expense_bot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/**
 * Сервис повторных попыток для идемпотентных операций записи в базу данных.
 *
 * <p>Используется для обновления embedding-вектора у уже сохраненного расхода.
 * Такая операция безопасна для повторов, потому что повторная запись того же
 * значения в ту же строку не создает дублей. Количество попыток и задержка
 * задаются через настройки {@code db.write-retry-*}.</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DatabaseRetryService {

    @Value("${db.write-retry-max-attempts:3}")
    private int maxAttempts;

    @Value("${db.write-retry-delay-ms:1000}")
    private long retryDelayMs;

    /**
     * Выполняет идемпотентную операцию записи с повторными попытками.
     *
     * <p>Повторяются только ошибки Spring DataAccessException. Если попытки
     * закончились, последняя ошибка пробрасывается выше, чтобы вызывающий код
     * мог залогировать или обработать ее по своему сценарию.</p>
     *
     * @param operation описание операции для логов
     * @param action действие записи в базу данных
     */
    public void executeIdempotentWrite(
            String operation,
            Runnable action
    ) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                action.run();
                return;
            } catch (DataAccessException e) {
                if (attempt == maxAttempts) {
                    throw e;
                }

                log.warn(
                        "Ошибка записи в БД при операции '{}', попытка {} из {}. Повтор через {} ms",
                        operation,
                        attempt,
                        maxAttempts,
                        retryDelayMs
                );

                sleepBeforeRetry();
            }
        }
    }

    /**
     * Делает паузу перед повторной попыткой записи в базу.
     */
    private void sleepBeforeRetry() {
        if (retryDelayMs <= 0) {
            return;
        }

        try {
            Thread.sleep(retryDelayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Ожидание повторной записи в БД было прервано",
                    e
            );
        }
    }
}
