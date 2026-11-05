package it.russello.contocorrente.controller;

import io.swagger.v3.oas.annotations.Operation;
import it.russello.contocorrente.dto.LoginRequest;
import it.russello.contocorrente.dto.LoginResponse;
import it.russello.contocorrente.dto.PersonRequest;
import it.russello.contocorrente.dto.PersonResponse;
import it.russello.contocorrente.service.AuthService;
import it.russello.contocorrente.service.PersonService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final PersonService personService;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @Operation(summary = "Registra un nuovo utente")
    @PostMapping("/signup")
    public ResponseEntity<PersonResponse> signup(@Validated(PersonRequest.Create.class) @RequestBody PersonRequest request) {
        PersonResponse response = personService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

}
