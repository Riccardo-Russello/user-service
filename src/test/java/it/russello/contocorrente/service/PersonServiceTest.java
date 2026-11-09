package it.russello.contocorrente.service;

import it.russello.contocorrente.dto.PersonRequest;
import it.russello.contocorrente.dto.PersonResponse;
import it.russello.contocorrente.entity.Person;
import it.russello.contocorrente.exception.ApiException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.RestClient;
import it.russello.contocorrente.repository.PersonRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PersonServiceTest {
    private static final LocalDate BIRTH_DATE = LocalDate.of(1990, 5, 15);
    @Mock
    private PersonRepository personRepository;
    @Mock
    private RestClient restClient;
    @Mock
    private PasswordEncoder passwordEncoder;
    @InjectMocks
    private PersonService personService;

    private static final String ACCOUNT_URL = "http://account.test";
    private static final String PERSON_ACCOUNTS = "/internal/accounts/by-person/{id}/exists";

    @BeforeEach
    void configureClientUrl() {
        ReflectionTestUtils.setField(personService, "accountBaseUrl", ACCOUNT_URL);
        var jwt = Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .subject("test")
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, java.util.List.of()));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    private static Person person(Long id) {
        Person person = new Person("Mario", "Rossi", BIRTH_DATE);
        ReflectionTestUtils.setField(person, "id", id);
        return person;
    }

    private static PersonRequest validRequest() {
        return new PersonRequest("Mario", "Rossi", BIRTH_DATE, "mario.rossi", "passwmario");
    }

    private static PersonRequest validUpdateRequest() {
        return new PersonRequest("Mario", "Rossi", BIRTH_DATE, null, null);
    }

    private static void assertApiError(ApiException exception, HttpStatus status, String code) {
        assertEquals(status, exception.getStatus());
        assertEquals(code, exception.getCode());
    }

    @Test
    void createValidPerson() {
        Person saved = person(1L);
        when(personRepository.save(any(Person.class))).thenReturn(saved);
        when(passwordEncoder.encode("passwmario")).thenReturn("encoded-password");
        PersonResponse response = personService.create(validRequest());
        assertEquals(new PersonResponse(1L, "Mario", "Rossi", BIRTH_DATE), response);
        ArgumentCaptor<Person> captor = ArgumentCaptor.forClass(Person.class);
        verify(personRepository).save(captor.capture());
        Person submitted = captor.getValue();
        assertNull(submitted.getId());
        assertEquals("Mario", submitted.getFirstName());
        assertEquals("Rossi", submitted.getLastName());
        assertEquals(BIRTH_DATE, submitted.getDateOfBirth());
        assertEquals("mario.rossi", submitted.getUsername());
        assertEquals("encoded-password", submitted.getPassword());
        assertEquals(it.russello.contocorrente.entity.Role.user, submitted.getRole());
        verify(personRepository).existsByUsername("mario.rossi");
        verify(passwordEncoder).encode("passwmario");
        verifyNoInteractions(restClient);
    }

    @Test
    void createWithExistingUsername() {
        when(personRepository.existsByUsername("mario.rossi")).thenReturn(true);
        ApiException exception = assertThrows(ApiException.class, () -> personService.create(validRequest()));
        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("USERNAME_ALREADY_EXISTS", exception.getCode());
        verify(personRepository, never()).save(any(Person.class));
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void createNames30CharAndBirthDateToday() {
        LocalDate today = LocalDate.now();
        PersonRequest request = new PersonRequest("a".repeat(30), "b".repeat(30), today, "more30.char", "passwmario");
        when(personRepository.save(any(Person.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(passwordEncoder.encode("passwmario")).thenReturn("passwmario");
        PersonResponse response = personService.create(request);

        assertEquals(request.firstName(), response.firstName());
        assertEquals(request.lastName(), response.lastName());
        assertEquals(today, response.dateOfBirth());
        verify(personRepository).save(any(Person.class));
    }

    @Test
    void findAll() {
        Person first = person(1L);
        Person second = new Person("Anna", "Bianchi", LocalDate.of(1985, 1, 10));
        ReflectionTestUtils.setField(second, "id", 2L);
        Pageable pageable = PageRequest.of(0, 10, Sort.by("id"));
        when(personRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(first, second), pageable, 2));
        Page<PersonResponse> responses = personService.findAll(pageable);
        assertEquals(List.of(PersonResponse.from(first), PersonResponse.from(second)), responses.getContent());
        assertEquals(0, responses.getNumber());
        assertEquals(10, responses.getSize());
        assertEquals(2L, responses.getTotalElements());
        assertEquals(1, responses.getTotalPages());
        verify(personRepository).findAll(pageable);
    }

    @Test
    void findAllReturnsEmptyPage() {
        Pageable pageable = PageRequest.of(0, 10, Sort.by("id"));
        when(personRepository.findAll(pageable)).thenReturn(Page.empty(pageable));
        Page<PersonResponse> responses = personService.findAll(pageable);
        assertTrue(responses.isEmpty());
        assertEquals(0, responses.getTotalElements());
        assertEquals(0, responses.getTotalPages());
        verify(personRepository).findAll(pageable);
    }

    @Test
    void findByIdReturnsPerson() {
        Person existing = person(1L);
        when(personRepository.findById(1L)).thenReturn(Optional.of(existing));
        PersonResponse response = personService.findById(1L);

        assertEquals(PersonResponse.from(existing), response);
        verify(personRepository).findById(1L);
    }

    @Test
    void findByIdMissingPerson() {
        when(personRepository.findById(1L)).thenReturn(Optional.empty());
        ApiException exception = assertThrows(ApiException.class, () -> personService.findById(1L));
        assertApiError(exception, HttpStatus.NOT_FOUND, "PERSON_NOT_FOUND");

    }

    @Test
    void updateExistingPerson() {
        Person existing = person(1L);
        PersonRequest request = new PersonRequest("Luigi", "Verdi", LocalDate.of(1988, 3, 20), null, null);
        when(personRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(personRepository.save(existing)).thenReturn(existing);
        PersonResponse response = personService.update(1L, request);
        assertEquals(new PersonResponse(1L, "Luigi", "Verdi", LocalDate.of(1988, 3, 20)), response);
        assertEquals("Luigi", existing.getFirstName());
        assertEquals("Verdi", existing.getLastName());
        assertEquals(request.dateOfBirth(), existing.getDateOfBirth());
        verify(personRepository).save(existing);
        verifyNoMoreInteractions(restClient);
    }

    @Test
    void updateMissingPerson() {
        when(personRepository.findById(1L)).thenReturn(Optional.empty());
        ApiException exception = assertThrows(ApiException.class, () -> personService.update(1L, validUpdateRequest()));
        assertApiError(exception, HttpStatus.NOT_FOUND, "PERSON_NOT_FOUND");
        verify(personRepository, never()).save(any(Person.class));
        verifyNoMoreInteractions(restClient);
    }

    @Test
    void updateMustNotChangeCredentials() {
        Person existing = person(1L);
        existing.setUsername("mario.rossi");
        existing.setPassword("encoded-password");
        existing.setRole(it.russello.contocorrente.entity.Role.admin);
        when(personRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(personRepository.save(existing)).thenReturn(existing);
        personService.update(1L, new PersonRequest("Anna", "Verdi", LocalDate.of(1995, 2, 10), null, null));
        assertEquals("mario.rossi", existing.getUsername());
        assertEquals("encoded-password", existing.getPassword());
        assertEquals(it.russello.contocorrente.entity.Role.admin, existing.getRole());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void deletePersonWithNoAccount() {
        Person existing = person(1L);
        when(personRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(stubGet(ACCOUNT_URL + PERSON_ACCOUNTS, 1L).body(Boolean.class)).thenReturn(false);

        personService.delete(1L);

        var order = inOrder(personRepository, restClient);
        order.verify(personRepository).findById(1L);
        order.verify(restClient).get();
        order.verify(personRepository).delete(existing);
        order.verify(personRepository).flush();
    }

    @Test
    void deletePersonWithAccount() {
        Person existing = person(1L);
        when(personRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(stubGet(ACCOUNT_URL + PERSON_ACCOUNTS, 1L).body(Boolean.class)).thenReturn(true);

        ApiException exception = assertThrows(ApiException.class, () -> personService.delete(1L));

        assertApiError(exception, HttpStatus.CONFLICT, "PERSON_HAS_ACCOUNT");
        verify(personRepository, never()).delete(any(Person.class));
        verify(personRepository, never()).flush();
    }

    @Test
    void deleteMissingPerson() {
        when(personRepository.findById(1L)).thenReturn(Optional.empty());

        ApiException exception = assertThrows(ApiException.class, () -> personService.delete(1L));

        assertApiError(exception, HttpStatus.NOT_FOUND, "PERSON_NOT_FOUND");

        verifyNoMoreInteractions(restClient);
        verify(personRepository, never()).delete(any(Person.class));
        verify(personRepository, never()).flush();
    }

    private RestClient.ResponseSpec stubGet(String url, Long id) {
        RestClient.RequestHeadersUriSpec<?> uriSpec =
                mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec<?> request =
                mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec response = mock(RestClient.ResponseSpec.class);

        doReturn(uriSpec).when(restClient).get();
        doReturn(request).when(uriSpec).uri(url, id);
        doReturn(request).when(request).headers(any());
        when(request.retrieve()).thenReturn(response);

        return response;
    }
}
