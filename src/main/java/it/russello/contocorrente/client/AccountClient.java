package it.russello.contocorrente.client;

import it.russello.contocorrente.dto.AccountResponse;
import it.russello.contocorrente.dto.PageResponse;
import it.russello.contocorrente.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@RequiredArgsConstructor
public class AccountClient {
    private final RestClient restClient;

    @Value("${clients.account.base-url}")
    private String accountBaseUrl;

    public boolean hasAccounts(Long id) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt)
                || !jwt.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException(
                    "Autenticazione JWT richiesta");
        }

        try {
            PageResponse<AccountResponse> page = restClient.get()
                    .uri(accountBaseUrl + "/api/account?personId={id}&size=1", id)
                    .headers(headers -> headers.setBearerAuth(jwt.getToken().getTokenValue()))
                    .retrieve()
                    .body(new ParameterizedTypeReference<PageResponse<AccountResponse>>() {});

            if (page != null && page.totalElements() != null) {
                return page.totalElements() > 0;
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