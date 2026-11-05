package it.russello.contocorrente.controller;

import it.russello.contocorrente.repository.PersonRepository;
import it.russello.contocorrente.security.AccountSecurity;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class InternalPersonController {
    private final PersonRepository personRepository;
    private final AccountSecurity accountSecurity;

    @GetMapping("/internal/persons/{id}/exists")
    @PreAuthorize("hasAnyRole('admin', 'user')")
    public boolean exists(@PathVariable("id") Long id) {
        return (accountSecurity.isAdmin() || id.equals(accountSecurity.currentPersonId()))
                && personRepository.existsById(id);
    }
}
