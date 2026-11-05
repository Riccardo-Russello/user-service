package it.russello.contocorrente.service;

import it.russello.contocorrente.dto.LoginRequest;
import it.russello.contocorrente.dto.LoginResponse;
import it.russello.contocorrente.exception.ApiException;
import it.russello.contocorrente.repository.PersonRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class AuthService {
    private final AuthenticationManager authenticationManager;
    private final JwtEncoder jwtEncoder;
    private final String issuer;
    private final PersonRepository personRepository;

    public AuthService(AuthenticationManager authenticationManager, JwtEncoder jwtEncoder, @Value("${security.jwt.issuer}") String issuer, PersonRepository personRepository) {
        this.authenticationManager = authenticationManager;
        this.jwtEncoder = jwtEncoder;
        this.issuer = issuer;
        this.personRepository = personRepository;
    }

    public LoginResponse login(LoginRequest request) {
        String username;
        List<String> authorities;
        try {
            var authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            request.username(), request.password()));
            username = authentication.getName();
            authorities = authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
        } catch (AuthenticationException e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "USername o password non corretti");
        }
        Long personId = personRepository.findByUsername(username).orElseThrow(() -> new ApiException(
                HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Utente non trovato")).getId();
        Instant now = Instant.now();
        long expiresIn = 3600;
        var claims = JwtClaimsSet.builder().issuer(issuer).subject(username).issuedAt(now).expiresAt(now.plusSeconds(expiresIn)).claim("personId", personId.toString()).claim("authorities", authorities).build();
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new LoginResponse(token, "Bearer", expiresIn);
    }
}
