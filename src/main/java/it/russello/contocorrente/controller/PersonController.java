package it.russello.contocorrente.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.russello.contocorrente.dto.PageResponse;
import it.russello.contocorrente.dto.PersonRequest;
import it.russello.contocorrente.dto.PersonResponse;
import it.russello.contocorrente.service.PersonService;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Persone", description = "Gestione delle persone")
@RestController
@RequestMapping("/api/person")
@RequiredArgsConstructor
public class PersonController {
    private final PersonService personService;

    @Operation(summary = "Elenca le persone con paginazione")
    @GetMapping
    public ResponseEntity<PageResponse<PersonResponse>> findAll(
            @ParameterObject
            @PageableDefault(page = 0, size = 10, sort = "id", direction = Sort.Direction.ASC)
            Pageable pageable
    ) {
        return ResponseEntity.ok(PageResponse.from(personService.findAll(pageable)));
    }

    @Operation(summary = "Cerca una persona per ID")
    @GetMapping("/{id}")
    public ResponseEntity<PersonResponse> findById(@PathVariable("id") Long id) {
        return ResponseEntity.status(HttpStatus.OK).body(personService.findById(id));
    }

    @Operation(summary = "Restituisce il profilo dell'utente autenticato")
    @GetMapping("/my_profile")
    public ResponseEntity<PersonResponse> myProfile() {
        return ResponseEntity.status(HttpStatus.OK).body(personService.myProfile());
    }

    @Operation(summary = "Aggiorna una persona")
    @PutMapping("/{id}")
    public ResponseEntity<PersonResponse> update(@PathVariable("id") Long id,
                                                 @Validated(PersonRequest.Update.class) @RequestBody PersonRequest request) {
        return ResponseEntity.status(HttpStatus.OK).body(personService.update(id, request));
    }

    @Operation(summary = "Elimina una persona")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        personService.delete(id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
