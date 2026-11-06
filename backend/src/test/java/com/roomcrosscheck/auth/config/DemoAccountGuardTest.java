package com.roomcrosscheck.auth.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;

/** 시연 프로필의 잘못된 DB·운영 환경·기본 자격 증명을 시작 단계에서 차단한다. */
class DemoAccountGuardTest {
    private final CommunitySecurityConfiguration config = new CommunitySecurityConfiguration();
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Test void rejectsProductionOrSharedDatabase() {
        var production = new MockEnvironment().withProperty("spring.datasource.url", "jdbc:mysql://localhost/rc_demo_test");
        production.setActiveProfiles("demo-login", "production");
        assertThrows(IllegalStateException.class, () -> config.demoAccounts(production, encoder));
        var shared = new MockEnvironment().withProperty("spring.datasource.url", "jdbc:mysql://localhost/roomcrosscheck");
        shared.setActiveProfiles("demo-login");
        assertThrows(IllegalStateException.class, () -> config.demoAccounts(shared, encoder));
    }

    @Test void rejectsMissingWeakOrRepeatedPasswords() {
        var environment = new MockEnvironment().withProperty("spring.datasource.url", "jdbc:mysql://localhost/rc_demo_test");
        environment.setActiveProfiles("demo-login");
        assertThrows(IllegalStateException.class, () -> config.demoAccounts(environment, encoder));
        environment.setProperty("DEMO_MEMBER1_PASSWORD", "short");
        assertThrows(IllegalStateException.class, () -> config.demoAccounts(environment, encoder));
        for (String key : new String[]{"DEMO_MEMBER1_PASSWORD", "DEMO_MEMBER2_PASSWORD", "DEMO_ADMIN_PASSWORD"})
            environment.setProperty(key, "test-shared-password");
        assertThrows(IllegalStateException.class, () -> config.demoAccounts(environment, encoder));
    }
}
