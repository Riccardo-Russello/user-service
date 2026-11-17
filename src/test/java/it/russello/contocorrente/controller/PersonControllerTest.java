package it.russello.contocorrente.controller;

import it.russello.contocorrente.dto.PersonRequest;
import it.russello.contocorrente.dto.PersonResponse;
import it.russello.contocorrente.exception.ApiException;
import it.russello.contocorrente.exception.ApiExceptionHandler;
import it.russello.contocorrente.service.AuthService;
import it.russello.contocorrente.service.PersonService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@WebMvcTest({PersonController.class, AuthController.class})
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiExceptionHandler.class)
public class PersonControllerTest {
    private static final LocalDate BIRTH_DATE = LocalDate.of(1990, 5, 15);
    private static final String VALID_JSON = """
            {
                "firstName" : "Mario",
                "lastName" : "Rossi",
                "dateOfBirth" : "1990-05-15",
                "username" : "mario.rossi",
                "password" : "passwmario"
            }
            """;
    private static final String UPDATE_JSON = """
            {
                "firstName": "Mario",
                "lastName": "Rossi",
                "dateOfBirth": "1990-05-15"
            }
            """;
    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private PersonService personService;
    @MockitoBean
    private AuthService authService;
    @MockitoBean
    private RestClient restClient;

    private static PersonRequest request() {
        return new PersonRequest("Mario", "Rossi", BIRTH_DATE, "mario.rossi", "passwmario");
    }

    private static PersonResponse response() {
        return new PersonResponse(1L, "Mario", "Rossi", BIRTH_DATE);
    }

    private static PersonRequest updateRequest() {
        return new PersonRequest("Mario", "Rossi", BIRTH_DATE, null, null);
    }

    @Test
    void createValidPerson() throws Exception {
        when(personService.create(request())).thenReturn(response());
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.firstName").value("Mario"))
                .andExpect(jsonPath("$.lastName").value("Rossi"))
                .andExpect(jsonPath("$.dateOfBirth").value("1990-05-15"));
        verify(personService).create(request());
    }

    @Test
    void createDuplicateUsername() throws Exception {
        when(personService.create(request()))
                .thenThrow(new ApiException(
                        HttpStatus.CONFLICT,
                        "USERNAME_ALREADY_EXISTS",
                        "Username già utilizzato"));
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_ALREADY_EXISTS"));
        verify(personService).create(request());
    }

    @Test
    void findAllReturnPerson() throws Exception {
        Pageable pageable = PageRequest.of(0, 10, Sort.by("id"));
        when(personService.findAll(pageable)).thenReturn(new PageImpl<>(List.of(response()), pageable, 1));
        mockMvc.perform(get("/api/person"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].firstName").value("Mario"))
                .andExpect(jsonPath("$.content[0].lastName").value("Rossi"))
                .andExpect(jsonPath("$.content[0].dateOfBirth").value("1990-05-15"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
        verify(personService).findAll(pageable);
    }

    @Test
    void findAllReturnsEmptyList() throws Exception {
        Pageable pageable = PageRequest.of(0, 10, Sort.by("id"));
        when(personService.findAll(pageable)).thenReturn(Page.empty(pageable));
        mockMvc.perform(get("/api/person"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
        verify(personService).findAll(pageable);
    }

    @Test
    void findAllUsesRequestedPaginationAndSorts() throws Exception {
        Pageable pageable = PageRequest.of(1, 1, Sort.by(Sort.Direction.DESC, "lastName"));
        when(personService.findAll(pageable)).thenReturn(new PageImpl<>(List.of(response()), pageable, 3));
        mockMvc.perform(get("/api/person")
                        .param("page", "1")
                        .param("size", "1")
                        .param("sort", "lastName,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(3));
        verify(personService).findAll(pageable);
    }

    @Test
    void findById() throws Exception {
        when(personService.findById(1L)).thenReturn(response());
        mockMvc.perform(get("/api/person/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.firstName").value("Mario"))
                .andExpect(jsonPath("$.lastName").value("Rossi"))
                .andExpect(jsonPath("$.dateOfBirth").value("1990-05-15"));
        verify(personService).findById(1L);
    }

    @Test
    void findByIdNotFound() throws Exception {
        when(personService.findById(1L)).thenThrow(new ApiException(HttpStatus.NOT_FOUND, "PERSON_NOT_FOUND", "Person non trovata"));
        mockMvc.perform(get("/api/person/1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PERSON_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Person non trovata"));
        verify(personService).findById(1L);
    }

    @Test
    void updatePerson() throws Exception {
        when(personService.update(1L, updateRequest())).thenReturn(response());
        mockMvc.perform(put("/api/person/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.firstName").value("Mario"))
                .andExpect(jsonPath("$.lastName").value("Rossi"))
                .andExpect(jsonPath("$.dateOfBirth").value("1990-05-15"));
        verify(personService).update(1L, updateRequest());
    }

    @Test
    void updatePersonNotFound() throws Exception {
        when(personService.update(1L, updateRequest())).thenThrow(new ApiException(HttpStatus.NOT_FOUND, "PERSON_NOT_FOUND", "Person non trovata"));
        mockMvc.perform(put("/api/person/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PERSON_NOT_FOUND"));
        verify(personService).update(1L, updateRequest());
    }

    @Test
    void deletePerson() throws Exception {
        mockMvc.perform(delete("/api/person/1"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(personService).delete(1L);
    }

    @Test
    void deletePersonHasAccount() throws Exception {
        doThrow(new ApiException(HttpStatus.CONFLICT, "PERSON_HAS_ACCOUNT", "Persona ha conti associati"))
                .when(personService).delete(1L);
        mockMvc.perform(delete("/api/person/1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PERSON_HAS_ACCOUNT"))
                .andExpect(jsonPath("$.message").value("Persona ha conti associati"));
        verify(personService).delete(1L);
    }

    @Test
    void deletePersonNotFound() throws Exception {
        doThrow(new ApiException(HttpStatus.NOT_FOUND, "PERSON_NOT_FOUND", "Person non trovata"))
                .when(personService).delete(1L);
        mockMvc.perform(delete("/api/person/1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PERSON_NOT_FOUND"));
        verify(personService).delete(1L);
    }

    @Test
    void createRejectJsonMalformed() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(personService);
    }

    @Test
    void createInvalidDateFormat() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "firstName": "Mario",
                                    "lastName": "Rossi",
                                    "dateOfBirth": "non-una-data"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(personService);
    }

    @Test
    void createMissingBody() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(personService);
    }

    @Test
    void findByIdNoNumericId() throws Exception {
        mockMvc.perform(get("/api/person/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        verifyNoInteractions(personService);
    }

    @Test
    void unexpectedInternalError() throws Exception {
        when(personService.findAll(any(Pageable.class))).thenThrow(new RuntimeException("Errore interno"));
        mockMvc.perform(get("/api/person"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Errore interno durante l'operazione"));
    }

}
