package edu.itc.shop.web;

import edu.itc.shop.domain.Role;
import edu.itc.shop.domain.UserAccount;
import edu.itc.shop.service.AccountService;
import edu.itc.shop.web.dto.RegisterRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api")
public class RegisterController {

    public record RegisterResponse(String username) {
    }

    public record MeResponse(String name, List<String> roles) {
    }

    private final AccountService accounts;

    public RegisterController(AccountService accounts) {
        this.accounts = accounts;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@RequestBody @Valid RegisterRequest req) {
        UserAccount created = accounts.register(req.username(), req.password(), Set.of(Role.CUSTOMER));
        return new RegisterResponse(created.getUsername()); // never return the password
    }

    @GetMapping("/me")
    public MeResponse me(Authentication auth) {
        List<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .sorted()
                .toList();
        return new MeResponse(auth.getName(), roles);
    }
}