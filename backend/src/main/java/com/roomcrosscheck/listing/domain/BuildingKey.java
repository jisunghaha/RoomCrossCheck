package com.roomcrosscheck.listing.domain;

import java.util.Locale;

/**
 * 거주 후기를 같은 건물끼리 묶는 키를 만든다.
 * <p>건축물대장 건물을 선택한 매물은 {@code ledger:} 접두사의 공식 건물 ID를 쓴다.
 * 그 밖에는 괄호 참고주소, 공백, 끝의 숫자·영문 동·층·호를 지운 기본 주소를 쓴다.
 * 주소 표기가 다르면 같은 건물이라도 다른 키가 될 수 있으므로 공식 조회 저장을 권장한다.
 */
public final class BuildingKey {
    private static final int MAX_LENGTH = 255;

    private BuildingKey() {}

    /**
     * 건축물대장 건물 ID로 키를 만든다.
     *
     * @param buildingId 건축HUB 관리 건축물대장 PK
     * @return {@code ledger:}로 시작하는 건물 키
     */
    public static String ofLedger(String buildingId) { return limit("ledger:" + buildingId.trim()); }

    /**
     * 등록 주소로 키를 만든다.
     *
     * @param address 사용자가 입력한 주소
     * @return {@code addr:}로 시작하는 건물 키
     */
    public static String ofAddress(String address) {
        String base = address == null ? "" : address.replaceAll("\\([^)]*\\)", " ").trim();
        // "26동 808호", "3층" 같은 상세주소만 지운다. "대치동"처럼 한글 행정동 이름은 남긴다.
        base = base.replaceAll("(?:[\\s,]+(?:지하)?[0-9A-Za-z-]+(?:동|층|호))+\\s*$", "");
        base = base.replaceAll("[\\s,]+", "").toLowerCase(Locale.ROOT);
        return limit("addr:" + base);
    }

    /**
     * 같은 회원의 중복 매물을 막는 전체 주소 키를 만든다. 건물 키와 달리 동·층·호를 남긴다.
     *
     * @param address 사용자가 입력한 주소
     * @return 괄호 참고주소·공백·쉼표를 지우고 소문자로 바꾼 주소, 최대 255자
     */
    public static String ofFullAddress(String address) {
        String base = address == null ? "" : address.replaceAll("\\([^)]*\\)", " ");
        return limit(base.replaceAll("[\\s,]+", "").toLowerCase(Locale.ROOT));
    }

    /**
     * 공개 후기에 표시할 건물 주소를 만든다. 개인정보가 드러나지 않도록 끝의 동·층·호를 지운다.
     *
     * @param address 사용자가 입력한 주소
     * @return 상세주소를 뺀 주소, 최대 160자
     */
    public static String publicLabel(String address) {
        String base = address == null ? "" : address.replaceAll("\\([^)]*\\)", " ");
        base = base.replaceAll("(?:[\\s,]+(?:지하)?[0-9A-Za-z-]+(?:동|층|호))+\\s*$", "")
            .replaceAll("\\s+", " ").strip();
        return base.length() > 160 ? base.substring(0, 160) : base;
    }

    private static String limit(String value) {
        return value.length() > MAX_LENGTH ? value.substring(0, MAX_LENGTH) : value;
    }
}
