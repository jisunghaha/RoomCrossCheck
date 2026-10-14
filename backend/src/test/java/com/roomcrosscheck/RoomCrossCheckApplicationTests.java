package com.roomcrosscheck;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class RoomCrossCheckApplicationTests {
    @Test
    void contextLoadsWithoutExternalServices() {
        // 기본 프로필은 외부 DB·인증 서비스를 연결하지 않아도 시작할 수 있어야 한다.
    }
}
