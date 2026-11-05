package it.russello.contocorrente.dto;

import it.russello.contocorrente.entity.Person;

import java.time.LocalDate;

public record PersonResponse(Long id, String firstName, String lastName, LocalDate dateOfBirth) {

    public static PersonResponse from(Person person) {
        return new PersonResponse(person.getId(),
                person.getFirstName(),
                person.getLastName(),
                person.getDateOfBirth());
    }
}
