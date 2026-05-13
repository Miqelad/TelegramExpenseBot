package com.paata.telegram_expense_bot.model.entity;

import com.paata.telegram_expense_bot.model.enums.ExpenseCategory;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Expense entity.
 *
 * <p>
 * Represents user expense.
 */
@Entity
@Table(name = "expenses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class Expense {

    /**
     * Primary key.
     */
    @Id
    @UuidGenerator
    @Column(nullable = false, updatable = false)
    private UUID uuid;

    /**
     * Telegram user id.
     */
    @Column(name = "user_id")
    private Long userId;

    /**
     * Expense amount.
     */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private ExpenseCategory category;

    /**
     * Expense description.
     */
    private String description;

    /**
     * Expense creation date.
     */
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}