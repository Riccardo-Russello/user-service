package it.russello.contocorrente.service;

import it.russello.contocorrente.dto.LoginRequest;
import it.russello.contocorrente.entity.Person;
import it.russello.contocorrente.exception.ApiException;
import it.russello.contocorrente.repository.PersonRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {
    @Mock
    AuthenticationManager authenticationManager;
    @Mock
    JwtEncoder jwtEncoder;
    @Mock
    PersonRepository personRepository;
    AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(authenticationManager, jwtEncoder, "test-issuer", personRepository);
    }

    private Authentication authenticatedUser() {
        return UsernamePasswordAuthenticationToken.authenticated("mario.rossi", null, List.of(new SimpleGrantedAuthority("ROLE_user")));
    }

    @Test
    void loginAuthenticatedBuildExpectedToken() {
        Person person = new Person("Mario", "Rossi", LocalDate.of(1990, 5, 15));
        ReflectionTestUtils.setField(person, "id", 10L);
        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(authenticatedUser());
        when(personRepository.findByUsername("mario.rossi")).thenReturn(Optional.of(person));
        Jwt jwt = Jwt.withTokenValue("signed-token").header("alg", "HS256").subject("mario.rossi").build();
        when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(jwt);
        Instant before = Instant.now();
        var response = authService.login(new LoginRequest("mario.rossi", "secret-password"));
        Instant after = Instant.now();
        assertEquals("signed-token", response.token());
        assertEquals("Bearer", response.tokenType());
        assertEquals(3600L, response.expiresIn());
        var authenticationCaptor = ArgumentCaptor.forClass(Authentication.class);
        verify(authenticationManager).authenticate(authenticationCaptor.capture());
        Authentication submitted = authenticationCaptor.getValue();
        assertEquals("mario.rossi", submitted.getName());
        assertEquals("secret-password", submitted.getCredentials());
        assertFalse(submitted.isAuthenticated());
        var tokenCaptor = ArgumentCaptor.forClass(JwtEncoderParameters.class);
        verify(jwtEncoder).encode(tokenCaptor.capture());
        JwtEncoderParameters parameters = tokenCaptor.getValue();
        JwtClaimsSet claims = parameters.getClaims();
        assertEquals(MacAlgorithm.HS256, parameters.getJwsHeader().getAlgorithm());
        assertEquals("test-issuer", claims.getClaim("iss"));
        assertEquals("mario.rossi", claims.getSubject());
        assertEquals("10", claims.getClaim("personId"));
        assertEquals(List.of("ROLE_user"), claims.getClaim("authorities"));
        assertFalse(claims.getIssuedAt().isBefore(before));
        assertFalse(claims.getIssuedAt().isAfter(after));
        assertEquals(claims.getIssuedAt().plusSeconds(3600), claims.getExpiresAt());
    }

    @Test
    void invalidCredentialsReturnUnauthorized() {
        when(authenticationManager.authenticate(any(Authentication.class))).thenThrow(new BadCredentialsException("Invalid"));
        ApiException exception = assertThrows(ApiException.class, () -> authService.login(new LoginRequest("mario.rossi", "wrong")));
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
        assertEquals("INVALID_CREDENTIALS", exception.getCode());
        verifyNoInteractions(personRepository, jwtEncoder);
    }

    @Test
    void authenticatedUsernameWithoutPersonReturnsUnauthorized() {
        when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(authenticatedUser());
        when(personRepository.findByUsername("mario.rossi")).thenReturn(Optional.empty());
        ApiException exception = assertThrows(ApiException.class, () -> authService.login(new LoginRequest("mario.rossi", "password")));
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
        assertEquals("INVALID_CREDENTIALS", exception.getCode());
        verifyNoInteractions(jwtEncoder);
    }
}
