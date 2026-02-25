package com.lab.banco;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("/")
    public String hello() {
        return "Lab Banco v1";
    }

    @GetMapping("/version")
    public String version() {
        return "v1";
    }
}
