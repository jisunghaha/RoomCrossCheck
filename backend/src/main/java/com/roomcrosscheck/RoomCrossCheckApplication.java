package com.roomcrosscheck;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 로컬 개발용 Spring Boot 서버의 시작점이다.
 *
 * <p>VS Code에서 이 클래스의 main 메서드를 실행하면 설정된 주소와 포트에 서버가 열린다.
 * 현재 기본 주소는 {@code 127.0.0.1:8080}이며, DB나 외부 인증 서비스 없이 시작한다.
 * 업무 API는 각 기능을 구현할 때 추가한다.
 */
@SpringBootApplication
public class RoomCrossCheckApplication {
    /**
     * Spring 설정을 읽고 내장 웹 서버를 시작한다.
     *
     * @param args Spring Boot에 전달할 실행 옵션
     */
    public static void main(String[] args) {
        SpringApplication.run(RoomCrossCheckApplication.class, args);
    }
}
