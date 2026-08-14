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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import jakarta.json.JsonValue;

import org.junit.jupiter.api.Test;

class JsonSchemaAttributeTest
{
    @Test
    void shouldExposePropertyAttributes()
    {
        JsonSchema schema = JsonSchema.of("""
            {"type":"object","properties":{
              "email":{"type":"string","x-labels":["A"]},
              "ssn":{"type":"string","x-flag":true},
              "note":{"type":"string","x-meta":{"kind":"A"}},
              "plain":{"type":"string"}
            }}""");

        JsonSchema email = schema.property("email");
        assertEquals(JsonValue.ValueType.ARRAY, email.attribute("x-labels").getValueType());
        assertEquals("A", email.attribute("x-labels").asJsonArray().getString(0));
        assertNull(email.attribute("x-flag"));

        JsonSchema ssn = schema.property("ssn");
        assertEquals(JsonValue.TRUE, ssn.attribute("x-flag"));

        JsonSchema note = schema.property("note");
        assertEquals("A", note.attribute("x-meta").asJsonObject().getString("kind"));

        JsonSchema plain = schema.property("plain");
        assertNull(plain.attribute("x-labels"));
        assertNull(plain.attribute("x-flag"));
        assertNull(plain.attribute("x-meta"));
    }

    @Test
    void shouldReturnNullForUnknownProperty()
    {
        JsonSchema schema = JsonSchema.of("{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\"}}}");

        assertNull(schema.property("missing"));
    }

    @Test
    void shouldReturnNullAttributeOnSchemaWithNoProperties()
    {
        JsonSchema schema = JsonSchema.of("{\"type\":\"string\"}");

        assertNull(schema.attribute("x-flag"));
        assertNull(schema.property("email"));
    }

    @Test
    void shouldNotInheritAttributeFromReferencedType()
    {
        JsonSchema schema = JsonSchema.of("""
            {"$defs":{"Email":{"type":"string","x-labels":["A"]}},
             "type":"object","properties":{
               "contact":{"$ref":"#/$defs/Email"}
             }}""", JsonSchema.Draft.DRAFT_07);

        // the annotation lives on the referenced type, not on the field (property) itself
        assertNull(schema.property("contact").attribute("x-labels"));
    }
}
