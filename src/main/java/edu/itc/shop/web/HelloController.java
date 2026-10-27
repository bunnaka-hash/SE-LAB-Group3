package edu.itc.shop.web;

import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController 
public class HelloController {
    
    public record Greeting(String message, Instant at) {}
    
    @GetMapping ("/hello")
    public Greeting hello(
            @RequestParam(defaultValue = "world!!") String name) {
        return new Greeting("Hello, " + name + "!", Instant.now());
        }
}
