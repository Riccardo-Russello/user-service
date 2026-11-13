package it.russello.contocorrente.controller;

import it.russello.contocorrente.dto.LoginRequest;
import it.russello.contocorrente.dto.LoginResponse;
import it.russello.contocorrente.exception.ApiException;
import it.russello.contocorrente.exception.ApiExceptionHandler;
import it.russello.contocorrente.service.AuthService;
import it.russello.contocorrente.service.PersonService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({PersonController.class, AuthController.class})
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiExceptionHandler.class)
public class ControllerValidationTest {
    @Autowired
    MockMvc mockMvc;
    @MockitoBean
    PersonService personService;
    @MockitoBean
    AuthService authService;
    @MockitoBean
    private RestClient restClient;

    private static String quote(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n") + "\"";
    }

    private static String person(String first, String last, String birth, String username, String password) {
        return """
                {
                  "firstName": %s,
                  "lastName": %s,
                  "dateOfBirth": %s,
                  "username": %s,
                  "password": %s
                }
                """.formatted(quote(first), quote(last), quote(birth), quote(username), quote(password));
    }

    static Stream<InvalidRequest> invalidRequests() {
        List<InvalidRequest> cases = new ArrayList<>();
        String birth = "1990-05-15";

        for (String method : List.of("POST", "PUT")) {
            String url = method.equals("POST") ? "/api/auth/signup" : "/api/person/1";
            String username = method.equals("POST") ? "mario.rossi" : null;
            String password = method.equals("POST") ? "Password123!" : null;
            for (String invalid : new String[]{null, "", "   ", "a".repeat(31)}) {
                cases.add(new InvalidRequest(method, url,
                        person(invalid, "Rossi", birth, username, password),
                        "INVALID_PERSON_DATA"));
                cases.add(new InvalidRequest(method, url,
                        person("Mario", invalid, birth, username, password),
                        "INVALID_PERSON_DATA"));
            }
            for (String invalid : new String[]{null, LocalDate.now().plusDays(1).toString()}) {
                cases.add(new InvalidRequest(method, url,
                        person("Mario", "Rossi", invalid, username, password), "INVALID_PERSON_DATA"));
            }
        }
        for (String username : new String[]{null, "", "   ", "ab", "a".repeat(31), "mario_rossi", "mario rossi"}) {
            cases.add(new InvalidRequest("POST", "/api/auth/signup",
                    person("Mario", "Rossi", birth, username, "Password123!"),
                    "INVALID_PERSON_DATA"));
        }
        for (String password : new String[]{
                null, "", "        ", "1234567", "a".repeat(73), "è".repeat(37)}) {
            cases.add(new InvalidRequest("POST", "/api/auth/signup",
                    person("Mario", "Rossi", birth, "mario.rossi", password),
                    "INVALID_PERSON_DATA"));
        }

        cases.add(new InvalidRequest("PUT", "/api/person/1",
                person("Mario", "Rossi", birth, "new.username", null),
                "INVALID_PERSON_DATA"));
        cases.add(new InvalidRequest("PUT", "/api/person/1",
                person("Mario", "Rossi", birth, null, "Password123!"),
                "INVALID_PERSON_DATA"));


        // Credenziali login.
        for (String invalid : new String[]{null, "", "   "}) {
            cases.add(new InvalidRequest("POST", "/api/auth/login",
                    "{\"username\":" + quote(invalid) + ",\"password\":\"Password123!\"}",
                    "INVALID_LOGIN_DATA"));

            cases.add(new InvalidRequest("POST", "/api/auth/login",
                    "{\"username\":\"mario.rossi\",\"password\":" + quote(invalid) + "}",
                    "INVALID_LOGIN_DATA"));
        }

        // Proprietà JSON sconosciute.
        for (String url : List.of(
                "/api/auth/signup",
                "/api/auth/login")) {

            cases.add(new InvalidRequest(
                    "POST", url, "{\"unexpected\":true}", "INVALID_REQUEST"));
        }

        return cases.stream();
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void invalidDataDoesNotReachService(InvalidRequest test) throws Exception {
        var request = test.method().equals("PUT")
                ? put(test.url())
                : post(test.url());

        mockMvc.perform(request
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(test.json()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(test.code()))
                .andExpect(jsonPath("$.message").isNotEmpty());

        verifyNoInteractions(personService, authService);
    }

    @Test
    void loginReturnsServiceResponse() throws Exception {
        LoginRequest request = new LoginRequest("mario.rossi", "Password123!");
        when(authService.login(request))
                .thenReturn(new LoginResponse("token", "Bearer", 3600));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"mario.rossi","password":"Password123!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600));

        verify(authService).login(request);
    }

    @Test
    void loginMapsInvalidCredentials() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new ApiException(
                        HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Credenziali errate"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"mario.rossi","password":"Password123!"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"{", "null", ""})
    void loginRejectsUnreadableBody(String body) throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(authService);
    }

    record InvalidRequest(String method, String url, String json, String code) {
    }
}

