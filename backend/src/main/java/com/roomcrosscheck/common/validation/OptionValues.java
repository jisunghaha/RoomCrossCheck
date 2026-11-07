package com.roomcrosscheck.common.validation;

import java.util.LinkedHashSet;
import java.util.List;

public final class OptionValues {
    private OptionValues() {}

    public static List<String> normalize(List<String> values) {
        if (values == null) return null;
        if (values.size() > 30) throw new IllegalArgumentException("옵션은 30개까지 입력할 수 있습니다.");
        var unique = new LinkedHashSet<String>();
        for (String value : values) {
            if (value == null || value.isBlank() || value.length() > 100
                    || value.chars().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("옵션은 제어문자 없는 1~100자 문자열이어야 합니다.");
            }
            unique.add(value.trim());
        }
        return List.copyOf(unique);
    }
}
