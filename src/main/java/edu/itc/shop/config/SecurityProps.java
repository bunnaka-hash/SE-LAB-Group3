package edu.itc.shop.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("shop.security")
@Validated
public record SecurityProps(@NotBlank @Size(min = 32) String jwtSecret,
                            Duration accessTokenTtl) {}