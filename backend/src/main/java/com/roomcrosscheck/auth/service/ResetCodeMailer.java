package com.roomcrosscheck.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * 비밀번호 재설정 인증 코드를 회원 이메일로 보낸다.
 * <p>MAIL_HOST가 설정되지 않았으면 메일 대신 서버 로그에 회원 번호와 코드를 남긴다(이메일 주소는 남기지 않음).
 * 운영에서는 Gmail 앱 비밀번호나 AWS SES SMTP 정보를 deploy/.env에 넣어 실제 발송한다.
 */
@Component
public class ResetCodeMailer {
    private static final Logger log = LoggerFactory.getLogger(ResetCodeMailer.class);
    private final ObjectProvider<JavaMailSender> senders;
    private final String host;
    private final String from;

    public ResetCodeMailer(ObjectProvider<JavaMailSender> senders, @Value("${spring.mail.host:}") String host,
            @Value("${mail.from:}") String from) {
        this.senders = senders;
        this.host = host;
        this.from = from;
    }

    /**
     * 인증 코드를 보낸다.
     *
     * @param userId 로그용 회원 번호
     * @param email 받는 주소
     * @param code 6자리 인증 코드
     * @throws ResponseStatusException 메일 서버 발송 실패 시 503
     */
    public void send(Long userId, String email, String code) {
        JavaMailSender sender = senders.getIfAvailable();
        if (host == null || host.isBlank() || sender == null) {
            log.warn("메일 설정(MAIL_HOST)이 없어 비밀번호 재설정 코드를 로그에 남깁니다. 회원 #{} 코드 {}", userId, code);
            return;
        }
        var message = new SimpleMailMessage();
        if (from != null && !from.isBlank()) message.setFrom(from);
        message.setTo(email);
        message.setSubject("[원룸체크] 비밀번호 재설정 인증 코드");
        message.setText("비밀번호 재설정 인증 코드: " + code + "\n\n10분 안에 입력해 주세요. 요청하지 않았다면 이 메일을 무시하세요.");
        try {
            sender.send(message);
        } catch (Exception error) {
            log.error("비밀번호 재설정 메일 발송 실패: 회원 #{} {}", userId, error.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "메일을 보내지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }
    }
}
