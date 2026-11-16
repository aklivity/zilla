/*
 * Copyright 2021-2026 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.runtime.common.json;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import jakarta.json.stream.JsonParser;

import org.junit.jupiter.api.Test;

class JsonSchemaDiagnosticPointerTest
{
    @Test
    void shouldPointAtAPropertyWhoseObjectValueHasKeysTheSchemaDoesNotName()
    {
        assertEquals("/a enum", first("{\"properties\":{\"a\":{\"enum\":[]}}}", "{\"a\":{\"x\":1}} "));
    }

    @Test
    void shouldPointAtAPropertyWhoseObjectValueIsNotAllowedByANotSubschema()
    {
        assertEquals("/a not", first("{\"properties\":{\"a\":{\"not\":{}}}}", "{\"a\":{\"x\":1,\"y\":2}} "));
    }

    @Test
    void shouldPointAtAPropertyWhoseSchemaIsFalse()
    {
        assertEquals("/a false", first("{\"properties\":{\"a\":false}}", "{\"a\":1} "));
    }

    @Test
    void shouldPointAtAPropertyWhoseSchemaIsFalseAndValueIsAnObject()
    {
        assertEquals("/a false", first("{\"properties\":{\"a\":false}}", "{\"a\":{\"x\":1}} "));
    }

    @Test
    void shouldPointAtAPropertyThroughAReference()
    {
        final String schema = "{\"$ref\":\"#/$defs/s\",\"$defs\":{\"s\":{\"properties\":{\"a\":false}}}}";

        assertEquals("/a false", first(schema, "{\"a\":{\"x\":1}} "));
    }

    @Test
    void shouldPointAtAPropertyThroughAReferenceInTheLatestDraft()
    {
        final String schema = "{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\",\"$ref\":\"#/$defs/s\"," +
            "\"$defs\":{\"s\":{\"properties\":{\"a\":{\"enum\":[]}}}}}";

        assertEquals("/a enum", first(schema, "{\"a\":{\"x\":1}} "));
    }

    @Test
    void shouldPointAtANestedArrayItem()
    {
        assertEquals("/a/1 type", first("{\"properties\":{\"a\":{\"items\":{\"type\":\"string\"}}}}", "{\"a\":[\"x\",2]} "));
    }

    @Test
    void shouldPointAtTheSameLocationWhateverTheKeyOrder()
    {
        final String schema = "{\"properties\":{\"a\":{\"enum\":[]}}}";

        assertEquals("/a enum", first(schema, "{\"b\":{\"x\":1},\"a\":{\"y\":2}} "));
    }

    @Test
    void shouldPointAtTheSameLocationFromSubschemasThatApplyToTheSameInstance()
    {
        final String schema = "{\"properties\":{\"a\":{\"type\":\"string\"}}," +
            "\"allOf\":[{\"properties\":{\"a\":{\"type\":\"string\"}}}]}";

        assertEquals(List.of("/a type", "/a type"), all(schema, "{\"a\":1} "));
    }

    @Test
    void shouldPointAtTheSameLocationFromAReferenceAndItsSiblingsInTheLatestDraft()
    {
        final String schema = "{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\",\"$ref\":\"#/$defs/s\"," +
            "\"properties\":{\"a\":{\"type\":\"string\"}},\"$defs\":{\"s\":{\"properties\":{\"a\":{\"type\":\"string\"}}}}}";

        assertEquals(List.of("/a type", "/a type"), all(schema, "{\"a\":1} "));
    }

    @Test
    void shouldPointAtAnItemOfAnArrayInsideAnObjectAfterEarlierSiblings()
    {
        final String schema = "{\"properties\":{\"a\":{\"type\":\"array\",\"items\":{\"type\":\"integer\"}}}}";

        assertEquals(List.of("/a/2 type"), all(schema, "{\"b\":[1,2],\"a\":[1,2,\"x\"]} "));
    }

    @Test
    void shouldPointAtAKeyTheSchemaForbids()
    {
        assertEquals("/b false", first("{\"properties\":{\"a\":{}},\"additionalProperties\":false}", "{\"b\":1} "));
    }

    @Test
    void shouldPointAtAKeyRoutedToAnAdditionalPropertiesSubschema()
    {
        assertEquals("/x type", first("{\"additionalProperties\":{\"type\":\"string\"}}", "{\"x\":1} "));
    }

    @Test
    void shouldPointAtAKeyRoutedToAPatternPropertiesSubschema()
    {
        assertEquals("/xa type", first("{\"patternProperties\":{\"^x\":{\"type\":\"string\"}}}", "{\"xa\":1} "));
    }

    @Test
    void shouldEscapeTheKeyInThePointer()
    {
        assertEquals("/a~1b~0c type", first("{\"additionalProperties\":{\"type\":\"string\"}}", "{\"a/b~c\":1} "));
    }

    @Test
    void shouldPointAtTheKeyWhenOneSubschemaNamesItAndAnotherRoutesItAsAdditional()
    {
        final String schema = "{\"properties\":{\"a\":{\"type\":\"string\"}}," +
            "\"allOf\":[{\"additionalProperties\":{\"type\":\"string\"}}]}";

        assertEquals(List.of("/a type", "/a type"), all(schema, "{\"a\":1} "));
    }

    @Test
    void shouldPointAtANestedKeyRoutedAsAdditionalUnderADeclaredOne()
    {
        final String schema = "{\"properties\":{\"a\":{\"additionalProperties\":{\"type\":\"string\"}}}}";

        assertEquals("/a/b type", first(schema, "{\"a\":{\"b\":1}} "));
    }

    @Test
    void shouldPointAtTheObjectWhoseKeyViolatesPropertyNames()
    {
        assertEquals(" maxLength", first("{\"propertyNames\":{\"maxLength\":1}}", "{\"abc\":1} "));
    }

    private static String first(
        String schema,
        String document)
    {
        final List<JsonSchemaDiagnostic> diagnostics = new ArrayList<>();
        final JsonParser parser = JsonSchema.of(schema).newParser(false,
            JsonEx.createParser(new ByteArrayInputStream(document.getBytes(UTF_8))), diagnostics::add);

        while (parser.hasNext())
        {
            parser.next();
        }

        return diagnostics.isEmpty() ? "none" : diagnostics.get(0).pointer() + " " + diagnostics.get(0).keyword();
    }

    private static List<String> all(
        String schema,
        String document)
    {
        final List<String> diagnostics = new ArrayList<>();
        final JsonParser parser = JsonSchema.of(schema).newParser(false,
            JsonEx.createParser(new ByteArrayInputStream(document.getBytes(UTF_8))),
            d -> diagnostics.add(d.pointer() + " " + d.keyword()));

        while (parser.hasNext())
        {
            parser.next();
        }

        return diagnostics;
    }
}
