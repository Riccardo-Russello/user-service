package it.russello.contocorrente.dto;

public record LoginResponse(String token, String tokenType, long expiresIn) {
}
