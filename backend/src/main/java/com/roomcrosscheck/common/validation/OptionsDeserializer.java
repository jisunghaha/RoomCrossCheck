package com.roomcrosscheck.common.validation;

import java.util.ArrayList;
import java.util.List;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.exc.InvalidFormatException;

public class OptionsDeserializer extends ValueDeserializer<List<String>> {
    @Override
    public List<String> deserialize(JsonParser parser, DeserializationContext context) {
        JsonNode node = (JsonNode) context.readTree(parser);
        try {
            if (!node.isArray()) throw new IllegalArgumentException("옵션은 문자열 배열 또는 null이어야 합니다.");
            if (node.size() > 30) throw new IllegalArgumentException("옵션은 30개까지 입력할 수 있습니다.");
            var values = new ArrayList<String>();
            for (int i = 0; i < node.size(); i++) {
                if (!node.get(i).isTextual()) throw new IllegalArgumentException("옵션 항목은 문자열이어야 합니다.");
                values.add(node.get(i).asString());
            }
            return OptionValues.normalize(values);
        } catch (IllegalArgumentException error) {
            throw InvalidFormatException.from(parser, error.getMessage(), null, List.class);
        }
    }
}
