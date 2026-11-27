package edu.itc.shop.web.dto;

import jakarta.validation.constraints.*;

public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 40) @Pattern(regexp = "[a-z0-9_.]+", message = "lower-case letters, digits, _ and . only") String username,
        @NotBlank @Size(min = 12, max = 72) @Pattern(regexp = "(?=.*\\d)(?=.*[a-z])(?=.*[A-Z]).*", message = "must contain an upper-case letter, a lower-case letter and a digit") String password) {
}