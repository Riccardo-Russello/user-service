package it.russello.contocorrente.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.*;
import jakarta.validation.groups.Default;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

public record PersonRequest(
        @NotBlank(message = "Nome obbligatorio") @Size(max = 30, message = "Nome non superiore a 30 caratteri") String firstName,
        @NotBlank(message = "Cognome obbligatorio") @Size(max = 30, message = "Cognome non superiore a 30 caratteri") String lastName,
        @NotNull(message = "Data di nascita obbligatoria") @PastOrPresent(message = "La data di nascita non può essere futura") LocalDate dateOfBirth,
        @NotBlank(groups = Create.class, message = "Username obbligatorio")
        @Pattern(
                regexp = "[a-zA-Z0-9.]{3,30}",
                groups = Create.class,
                message = "Username da 3 a 30 caratteri: lettere, numeri e punto")
        @Null(
                groups = Update.class,
                message = "Questo endpoint aggiorna solo i dati anagrafici")
        String username,

        @NotBlank(groups = Create.class, message = "Password obbligatoria")
        @Size(
                min = 8,
                groups = Create.class,
                message = "Password di almeno 8 caratteri"
        )
        @Null(
                groups = Update.class,
                message = "Questo endpoint aggiorna solo i dati anagrafici"
        ) String password) {
    @JsonIgnore
    @AssertTrue(
            groups = Create.class,
            message = "Password non superiore a 72 byte UTF-8"
    )
    public boolean isPasswordWithinByteLimit() {
        return password == null
                || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    public interface Create extends Default {
    }

    public interface Update extends Default {
    }
}
