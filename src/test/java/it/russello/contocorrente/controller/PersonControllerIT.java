package it.russello.contocorrente.controller;

import it.russello.contocorrente.client.InternalClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.mockito.Mockito.when;

import it.russello.contocorrente.entity.Person;
import it.russello.contocorrente.repository.PersonRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static it.russello.contocorrente.support.TestJwt.admin;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class PersonControllerIT {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PersonRepository personRepository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    @MockitoBean
    private InternalClient internalClient;

    @Test
    void createPerson() throws Exception {
        long counterBefore = personRepository.count();
        var response = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "firstName" : "Mario",
                                    "lastName" : "Rossi",
                                    "dateOfBirth" : "1990-05-15",
                                    "username" : "mario.rossi",
                                    "password" : "passwmario"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.firstName").value("Mario"))
                .andExpect(jsonPath("$.lastName").value("Rossi"))
                .andExpect(jsonPath("$.dateOfBirth").value("1990-05-15"))
                .andReturn();
        entityManager.flush();
        entityManager.clear();
        assertEquals(counterBefore + 1, personRepository.count());

        Number responseId = com.jayway.jsonpath.JsonPath.read(response.getResponse().getContentAsString(), "$.id");

        Person saved = personRepository.findById(responseId.longValue()).orElseThrow();
        assertEquals(saved.getFirstName(), "Mario");
        assertEquals(saved.getLastName(), "Rossi");
        assertEquals(saved.getDateOfBirth(), LocalDate.of(1990, 5, 15));
        assertEquals("mario.rossi", saved.getUsername());
        assertNotEquals("passwmario", saved.getPassword());
        assertTrue(passwordEncoder.matches("passwmario", saved.getPassword()));
        assertEquals(it.russello.contocorrente.entity.Role.user, saved.getRole());
    }

    @Test
    void findAllPerson() throws Exception {
        Person person = new Person("Mario", "Rossi", LocalDate.of(1990, 5, 15));
        person.setUsername("mario.rossi");
        person.setPassword("passwmario");
        person = personRepository.saveAndFlush(person);
        Long id = person.getId();
        mockMvc.perform(get("/api/person").with(admin(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id))
                .andExpect(jsonPath("$.content[0].firstName").value("Mario"))
                .andExpect(jsonPath("$.content[0].lastName").value("Rossi"))
                .andExpect(jsonPath("$.content[0].dateOfBirth").value("1990-05-15"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void findPersonById() throws Exception {
        Person person = new Person("Mario", "Rossi", LocalDate.of(1990, 5, 15));
        person.setUsername("mario.rossi");
        person.setPassword("passwmario");
        person = personRepository.saveAndFlush(person);
        Long id = person.getId();
        entityManager.clear();

        mockMvc.perform(get("/api/person/{id}", id).with(admin(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.firstName").value("Mario"))
                .andExpect(jsonPath("$.lastName").value("Rossi"))
                .andExpect(jsonPath("$.dateOfBirth").value("1990-05-15"));
    }

    @Test
    void updatePerson() throws Exception {
        Person person = new Person("Mario", "Rossi", LocalDate.of(1990, 5, 15));
        person.setUsername("mario.rossi");
        person.setPassword("passwmario");
        person = personRepository.saveAndFlush(person);
        Long id = person.getId();
        entityManager.clear();
        long counterBefore = personRepository.count();

        mockMvc.perform(put("/api/person/{id}", id).with(admin(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "firstName": "Anna",
                                    "lastName": "Verdi",
                                    "dateOfBirth": "1995-02-10"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.firstName").value("Anna"))
                .andExpect(jsonPath("$.lastName").value("Verdi"))
                .andExpect(jsonPath("$.dateOfBirth").value("1995-02-10"));
        entityManager.flush();
        entityManager.clear();
        assertEquals(counterBefore, personRepository.count());

        Person updated = personRepository.findById(id).orElseThrow();
        assertEquals("Anna", updated.getFirstName());
        assertEquals("Verdi", updated.getLastName());
        assertEquals(LocalDate.of(1995, 2, 10), updated.getDateOfBirth());
        assertEquals("mario.rossi", updated.getUsername());
        assertEquals("passwmario", updated.getPassword());
    }

    @Test
    void updateRejectsCredentialsAndLeavesPersonUnchanged() throws Exception {
        Person person = new Person("Mario", "Rossi", LocalDate.of(1990, 5, 15));
        person.setUsername("update.credentials");
        person.setPassword("original-password");
        personRepository.saveAndFlush(person);
        Long id = person.getId();
        mockMvc.perform(put("/api/person/{id}", id)
                        .with(admin(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName":"Anna",
                                  "lastName":"Verdi",
                                  "dateOfBirth":"1995-02-10",
                                  "username":"changed.username",
                                  "password":"Password123!"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PERSON_DATA"));
        entityManager.clear();
        Person unchanged = personRepository.findById(id).orElseThrow();
        assertEquals("Mario", unchanged.getFirstName());
        assertEquals("update.credentials", unchanged.getUsername());
        assertEquals("original-password", unchanged.getPassword());
    }

    @Test
    void deletePerson() throws Exception {
        Person person = new Person("Mario", "Rossi", LocalDate.of(1990, 5, 15));
        person.setUsername("mario.rossi");
        person.setPassword("passwmario");
        person = personRepository.saveAndFlush(person);
        Long id = person.getId();
        entityManager.clear();
        long counterBefore = personRepository.count();

        mockMvc.perform(delete("/api/person/{id}", id).with(admin(1L)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        entityManager.flush();
        entityManager.clear();

        assertFalse(personRepository.existsById(id));
        assertEquals(counterBefore - 1, personRepository.count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "PUT", "DELETE"})
    void missingPersonReturnsNotFound(String method) throws Exception {
        var request = switch (method) {
            case "GET" -> get("/api/person/{id}", Long.MAX_VALUE);
            case "PUT" -> put("/api/person/{id}", Long.MAX_VALUE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "firstName":"Anna",
                              "lastName":"Verdi",
                              "dateOfBirth":"1995-02-10"
                            }
                            """);
            case "DELETE" -> delete("/api/person/{id}", Long.MAX_VALUE);
            default -> throw new IllegalArgumentException(method);
        };
        long before = personRepository.count();
        mockMvc.perform(request.with(admin(1L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PERSON_NOT_FOUND"));
        assertEquals(before, personRepository.count());
    }

    @Test
    void personWithAccountCannotBeDeleted() throws Exception {
        Person person = new Person(
                "Mario", "Rossi", LocalDate.of(1990, 5, 15));
        person.setUsername("delete.person");
        person.setPassword("fixture-password");
        personRepository.saveAndFlush(person);
        when(internalClient.check("http://account.test",
                "/internal/accounts/by-person/{id}/exists", person.getId())).thenReturn(true);
        mockMvc.perform(delete("/api/person/{id}", person.getId())
                        .with(admin(person.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PERSON_HAS_ACCOUNT"));
        entityManager.clear();
        assertTrue(personRepository.existsById(person.getId()));
    }
}
