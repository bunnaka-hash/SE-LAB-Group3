package edu.itc.shop.web.dto;

public record TokenResponse(String token, String tokenType, long expiresIn) {}