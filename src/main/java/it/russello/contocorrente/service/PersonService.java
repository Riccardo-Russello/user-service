package it.russello.contocorrente.service;

import it.russello.contocorrente.dto.PersonRequest;
import it.russello.contocorrente.dto.PersonResponse;
import it.russello.contocorrente.entity.Person;
import it.russello.contocorrente.exception.ApiException;
import it.russello.contocorrente.repository.PersonRepository;
import it.russello.contocorrente.security.AccountSecurity;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class PersonService {
    private final PersonRepository personRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountSecurity accountSecurity;
    private final RestClient restClient;

    @Value("${clients.account.base-url}")
    private String accountBaseUrl;

    @PreAuthorize("isAnonymous() or hasRole('admin')")
    public PersonResponse create(PersonRequest request) {
        if (personRepository.existsByUsername(request.username())) {
            throw new ApiException(HttpStatus.CONFLICT, "USERNAME_ALREADY_EXISTS", "Username già utilizzato");
        }
        Person person = new Person(request.firstName(), request.lastName(), request.dateOfBirth());
        person.setUsername(request.username());
        person.setPassword(passwordEncoder.encode(request.password()));
        Person savedPerson = personRepository.save(person);
        return PersonResponse.from(savedPerson);
    }

    @PreAuthorize("hasRole('admin')")
    @Transactional(readOnly = true)
    public Page<PersonResponse> findAll(Pageable pageable) {
        return personRepository.findAll(pageable)
                .map(PersonResponse::from);
    }

    @PreAuthorize("hasRole('admin')")
    public PersonResponse findById(Long id) {
        return PersonResponse.from(requirePerson(id));
    }

    @PreAuthorize("hasAnyRole('admin', 'user')")
    public PersonResponse myProfile() {
        Long personId = accountSecurity.currentPersonId();
        return PersonResponse.from(requirePerson(personId));
    }

    @PreAuthorize("hasRole('admin')")
    public PersonResponse update(Long id, PersonRequest request) {
        Person person = requirePerson(id);
        person.setFirstName(request.firstName());
        person.setLastName(request.lastName());
        person.setDateOfBirth(request.dateOfBirth());

        Person savedPerson = personRepository.save(person);
        return PersonResponse.from(savedPerson);
    }

    @PreAuthorize("hasRole('admin')")
    public void delete(Long id) {
        Person person = requirePerson(id);
        if(hasAccounts(id)) {
            throw new ApiException(HttpStatus.CONFLICT, "PERSON_HAS_ACCOUNT", "La persona ha conti associati");
        }

        personRepository.delete(person);
        personRepository.flush();
    }

    private Person requirePerson(Long id) {
        return personRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PERSON_NOT_FOUND", "Persona non trovata"));
    }

    private boolean hasAccounts(Long id) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt)
                || !jwt.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException(
                    "Autenticazione JWT richiesta");
        }

        try {
            Map<?, ?> page = restClient.get()
                    .uri(accountBaseUrl + "/api/account?personId={id}&size=1", id)
                    .headers(headers -> headers.setBearerAuth(jwt.getToken().getTokenValue()))
                    .retrieve()
                    .body(Map.class);
            if (page != null && page.get("totalElements") instanceof Number total) {
                return total.longValue() > 0;
            }
        }catch (RestClientException ex) {
            throw upstreamUnavailable();
        }

        throw upstreamUnavailable();
    }

    private ApiException upstreamUnavailable() {
        return new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "UPSTREAM_UNAVAILABLE",
                "Servizio remoto non disponibile");
    }
}
