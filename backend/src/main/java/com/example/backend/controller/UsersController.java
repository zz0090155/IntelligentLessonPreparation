package com.example.backend.controller;

import com.example.backend.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/user")
public class UsersController {

    @Autowired
    private UserService userService;

    @GetMapping("/code")
    public ResponseEntity<Map<String, Object>> sendCodeByEmail(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String account
    ) {
        return userService.sendCodeByEmail(email, phone, name, account);
    }
}