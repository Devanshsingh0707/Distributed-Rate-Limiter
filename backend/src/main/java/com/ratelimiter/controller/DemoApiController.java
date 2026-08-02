package com.ratelimiter.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*") // Allow requests from React dashboard running on any origin
public class DemoApiController {

    @GetMapping("/login")
    public ResponseEntity<Map<String, String>> login() {
        return ResponseEntity.ok(Map.of(
            "status", "success",
            "message", "Demo endpoint (login) response. Authenticated.",
            "route", "/api/login"
        ));
    }

    @GetMapping("/search")
    public ResponseEntity<Map<String, String>> search() {
        return ResponseEntity.ok(Map.of(
            "status", "success",
            "message", "Demo endpoint (search) response. Found 10 results.",
            "route", "/api/search"
        ));
    }

    @GetMapping("/payment")
    public ResponseEntity<Map<String, String>> payment() {
        return ResponseEntity.ok(Map.of(
            "status", "success",
            "message", "Demo endpoint (payment) response. Transaction authorized.",
            "route", "/api/payment"
        ));
    }

    @GetMapping("/products")
    public ResponseEntity<Map<String, String>> products() {
        return ResponseEntity.ok(Map.of(
            "status", "success",
            "message", "Demo endpoint (products) response. Listing product database.",
            "route", "/api/products"
        ));
    }
}
