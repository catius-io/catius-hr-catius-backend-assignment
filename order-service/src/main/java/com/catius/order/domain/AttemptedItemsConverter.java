package com.catius.order.domain;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.List;

/**
 * pending_compensations.attempted_items_json 컬럼의 JSON 직렬화를 담당.
 * SQLite는 native JSON 컬럼 타입이 없으므로 TEXT로 저장하고 어플리케이션 측에서 파싱.
 */
@Converter
public class AttemptedItemsConverter implements AttributeConverter<List<AttemptedItem>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<AttemptedItem>> TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(List<AttemptedItem> attribute) {
        try {
            return MAPPER.writeValueAsString(attribute == null ? List.of() : attribute);
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialize attempted items", e);
        }
    }

    @Override
    public List<AttemptedItem> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return MAPPER.readValue(dbData, TYPE);
        } catch (Exception e) {
            throw new IllegalStateException("failed to deserialize attempted items: " + dbData, e);
        }
    }
}
