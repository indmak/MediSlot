package com.medislot.service.external;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JsonPathsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void resolvesObjectAndArrayPaths() {
        JsonNode node = mapper.readTree("{\"a\":{\"b\":[{\"c\":1},{\"c\":2}]}}");
        assertEquals("2", JsonPaths.text(JsonPaths.at(node, "a.b[1].c")));
        assertNull(JsonPaths.at(node, "a.x"));
        assertNull(JsonPaths.at(node, "a.b[9].c"));
    }

    @Test
    void substitutesPlaceholders() {
        assertEquals("https://x/123/", JsonPaths.subst("https://x/{{id}}/", Map.of("id", "123")));
        assertEquals("", JsonPaths.subst("{{missing}}", Map.of("id", "1")));
    }
}
