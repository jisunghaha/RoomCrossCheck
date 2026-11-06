package com.roomcrosscheck.auth.controller;

import java.util.Map;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 시연 로그인 제공 여부를 알리고 CSRF 쿠키를 발급받는 공개 경로를 제공한다. */
@RestController
public class DemoLoginController {
    private final Environment environment;
    public DemoLoginController(Environment environment) { this.environment = environment; }

    @GetMapping("/api/auth/config")
    public Map<String, Boolean> config() {
        return Map.of("demoEnabled", environment.acceptsProfiles(Profiles.of("demo-login")));
    }

    @GetMapping("/api/auth/csrf")
    public Map<String, Boolean> csrf() { return Map.of("ready", true); }
}
