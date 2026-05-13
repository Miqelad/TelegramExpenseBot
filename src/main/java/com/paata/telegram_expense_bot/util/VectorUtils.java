package com.paata.telegram_expense_bot.util;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Утилита для работы с embedding-векторами.
 */
public class VectorUtils {

    /**
     * Конвертирует Java-вектор в строковый формат PostgreSQL pgvector.
     *
     * <p>Например, список {@code [0.1, 0.2, 0.3]} превращается в строку
     * {@code "[0.1,0.2,0.3]"}, которую можно привести к типу {@code vector}
     * в SQL-запросе.</p>
     *
     * @param vector embedding-вектор
     * @return строка в формате pgvector
     */
    public static String toPgVector(List<Float> vector) {

        return vector.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(
                        ",",
                        "[",
                        "]"
                ));
    }
}
