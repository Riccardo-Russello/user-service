package it.russello.contocorrente.controller;

import it.russello.contocorrente.entity.Person;
import it.russello.contocorrente.repository.PersonRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static it.russello.contocorrente.support.TestJwt.admin;
import static it.russello.contocorrente.support.TestJwt.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthorizationIT {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    PersonRepository personRepository;

    Person owner;
    Person other;

    @BeforeEach
    void setUp() {
        owner = savePerson("auth.owner");
        other = savePerson("auth.other");

    }

    private Person savePerson(String username) {
        Person person = new Person(
                "Mario", "Rossi", LocalDate.of(1990, 5, 15));
        person.setUsername(username);
        person.setPassword("fixture-password");
        return personRepository.saveAndFlush(person);
    }

    private MockHttpServletRequestBuilder operation(String operation) {
        return switch (operation) {
            case "PERSON_LIST" -> get("/api/person");
            case "PERSON_READ" -> get("/api/person/{id}", owner.getId());
            case "PERSON_UPDATE" -> put("/api/person/{id}", owner.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"firstName":"Anna","lastName":"Verdi","dateOfBirth":"1990-05-15"}
                            """);
            case "PERSON_DELETE" -> delete("/api/person/{id}", other.getId());

            default -> throw new IllegalArgumentException(operation);
        };
    }

    @ParameterizedTest
    @ValueSource(strings = {"PERSON_LIST", "PERSON_READ", "PERSON_UPDATE", "PERSON_DELETE"})
    void protectedOperationsRequireAuthentication(String operation) throws Exception {
        mockMvc.perform(operation(operation))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PERSON_LIST", "PERSON_READ", "PERSON_UPDATE", "PERSON_DELETE"})
    void regularUserCannotPerformAdminOperations(String operation) throws Exception {
        mockMvc.perform(operation(operation).with(user(owner.getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PERSON_LIST", "PERSON_READ", "PERSON_UPDATE"})
    void adminCanPerformAllowedOperations(String operation) throws Exception {
        int expected = 200;

        mockMvc.perform(operation(operation).with(admin(owner.getId())))
                .andExpect(status().is(expected));
    }

    @Test
    void authenticatedUserCannotRegisterAnotherPerson() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .with(user(owner.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName":"Anna",
                                  "lastName":"Verdi",
                                  "dateOfBirth":"1990-05-15",
                                  "username":"new.person",
                                  "password":"Password123!"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

}