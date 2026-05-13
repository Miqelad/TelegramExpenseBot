package com.paata.telegram_expense_bot.util;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Конвертирует vector
 * в pgvector string format.
 */
public class VectorUtils {

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