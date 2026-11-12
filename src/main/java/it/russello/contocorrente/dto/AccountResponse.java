package it.russello.contocorrente.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AccountResponse(
        Long id,
        Long personId,
        String accountNumber,
        LocalDateTime openedAt,
        BigDecimal balance
) {}