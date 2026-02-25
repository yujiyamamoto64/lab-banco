package com.lab.banco;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    private final String appVersion;

    public HelloController(@Value("${app.version}") String appVersion) {
        this.appVersion = appVersion;
    }

    @GetMapping("/")
    public String hello() {
        return "Lab Banco " + appVersion;
    }

    @GetMapping("/version")
    public String version() {
        return appVersion;
    }
}
