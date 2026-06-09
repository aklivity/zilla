/*
 * Copyright 2021-2024 Aklivity Inc
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.json.stream.JsonParser;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

class JsonSchemaTest
{
    @Test
    void shouldValidateScalarTypes()
    {
        assertTrue(valid("{\"type\":\"string\"}", "\"hi\""));
        assertFalse(valid("{\"type\":\"string\"}", "5"));
        assertTrue(valid("{\"type\":\"boolean\"}", "true"));
        assertTrue(valid("{\"type\":\"boolean\"}", "false"));
        assertFalse(valid("{\"type\":\"boolean\"}", "\"x\""));
        assertTrue(valid("{\"type\":\"null\"}", "null"));
        assertFalse(valid("{\"type\":\"null\"}", "0"));
    }

    @Test
    void shouldDistinguishIntegerFromNumber()
    {
        assertTrue(valid("{\"type\":\"integer\"}", "42"));
        assertFalse(valid("{\"type\":\"integer\"}", "1.5"));
        assertTrue(valid("{\"type\":\"number\"}", "1.5"));
        assertTrue(valid("{\"type\":\"number\"}", "42"));
    }

    @Test
    void shouldAcceptAnyOfMultipleTypes()
    {
        assertTrue(valid("{\"type\":[\"string\",\"null\"]}", "\"x\""));
        assertTrue(valid("{\"type\":[\"string\",\"null\"]}", "null"));
        assertFalse(valid("{\"type\":[\"string\",\"null\"]}", "5"));
    }

    @Test
    void shouldValidateEnum()
    {
        assertTrue(valid("{\"enum\":[\"a\",\"b\"]}", "\"b\""));
        assertFalse(valid("{\"enum\":[\"a\",\"b\"]}", "\"c\""));
        assertTrue(valid("{\"enum\":[1,2,3]}", "2"));
        assertFalse(valid("{\"enum\":[1,2,3]}", "4"));
    }

    @Test
    void shouldValidateConst()
    {
        assertTrue(valid("{\"const\":\"fixed\"}", "\"fixed\""));
        assertFalse(valid("{\"const\":\"fixed\"}", "\"other\""));
        assertTrue(valid("{\"const\":7}", "7.0"));
    }

    @Test
    void shouldValidateNumericBounds()
    {
        assertTrue(valid("{\"minimum\":5,\"maximum\":10}", "5"));
        assertTrue(valid("{\"minimum\":5,\"maximum\":10}", "10"));
        assertFalse(valid("{\"minimum\":5,\"maximum\":10}", "4"));
        assertFalse(valid("{\"minimum\":5,\"maximum\":10}", "11"));
        assertFalse(valid("{\"exclusiveMinimum\":5}", "5"));
        assertTrue(valid("{\"exclusiveMinimum\":5}", "6"));
        assertFalse(valid("{\"exclusiveMaximum\":5}", "5"));
        assertTrue(valid("{\"exclusiveMaximum\":5}", "4"));
    }

    @Test
    void shouldValidateMultipleOf()
    {
        assertTrue(valid("{\"multipleOf\":2}", "6"));
        assertFalse(valid("{\"multipleOf\":2}", "7"));
    }

    @Test
    void shouldValidateStringLengthAndPattern()
    {
        assertTrue(valid("{\"minLength\":2,\"maxLength\":4}", "\"abc\""));
        assertFalse(valid("{\"minLength\":2}", "\"a\""));
        assertFalse(valid("{\"maxLength\":4}", "\"abcde\""));
        assertTrue(valid("{\"pattern\":\"^a.*z$\"}", "\"abcz\""));
        assertFalse(valid("{\"pattern\":\"^a.*z$\"}", "\"abc\""));
    }

    @Test
    void shouldCountStringLengthAsCodePoints()
    {
        assertTrue(valid("{\"maxLength\":1}", "\"é\""));
    }

    @Test
    void shouldValidateArrayItemsAndSize()
    {
        assertTrue(valid("{\"type\":\"array\",\"items\":{\"type\":\"integer\"}}", "[1,2,3]"));
        assertFalse(valid("{\"type\":\"array\",\"items\":{\"type\":\"integer\"}}", "[1,\"x\"]"));
        assertFalse(valid("{\"minItems\":2}", "[1]"));
        assertTrue(valid("{\"minItems\":2}", "[1,2]"));
        assertFalse(valid("{\"maxItems\":2}", "[1,2,3]"));
    }

    @Test
    void shouldValidateObjectPropertiesAndRequired()
    {
        String schema = "{\"type\":\"object\",\"properties\":{\"n\":{\"type\":\"integer\"}},\"required\":[\"n\"]}";
        assertTrue(valid(schema, "{\"n\":3}"));
        assertFalse(valid(schema, "{\"n\":\"x\"}"));
        assertFalse(valid(schema, "{\"other\":1}"));
        assertTrue(valid(schema, "{\"n\":3,\"extra\":true}"));
    }

    @Test
    void shouldRejectAdditionalPropertiesWhenDisallowed()
    {
        String schema = "{\"properties\":{\"a\":{\"type\":\"integer\"}},\"additionalProperties\":false}";
        assertTrue(valid(schema, "{\"a\":1}"));
        assertFalse(valid(schema, "{\"a\":1,\"b\":2}"));
    }

    @Test
    void shouldValidateAdditionalPropertiesAgainstSchema()
    {
        String schema = "{\"additionalProperties\":{\"type\":\"string\"}}";
        assertTrue(valid(schema, "{\"x\":\"hi\"}"));
        assertFalse(valid(schema, "{\"x\":5}"));
    }

    @Test
    void shouldValidateMinAndMaxProperties()
    {
        assertFalse(valid("{\"minProperties\":2}", "{\"a\":1}"));
        assertTrue(valid("{\"minProperties\":2}", "{\"a\":1,\"b\":2}"));
        assertFalse(valid("{\"maxProperties\":1}", "{\"a\":1,\"b\":2}"));
    }

    @Test
    void shouldValidateNestedStructures()
    {
        String schema = "{\"type\":\"object\",\"properties\":{" +
            "\"items\":{\"type\":\"array\",\"items\":{\"type\":\"object\"," +
            "\"properties\":{\"id\":{\"type\":\"integer\"}},\"required\":[\"id\"]}}}}";
        assertTrue(valid(schema, "{\"items\":[{\"id\":1},{\"id\":2}]}"));
        assertFalse(valid(schema, "{\"items\":[{\"id\":1},{\"name\":\"x\"}]}"));
    }

    @Test
    void shouldAcceptAnythingForEmptySchema()
    {
        assertTrue(valid("{}", "{\"a\":[1,{\"b\":2}],\"c\":null}"));
        assertTrue(valid("{}", "42"));
    }

    @Test
    void shouldRejectAllInstancesForFalseSubschema()
    {
        assertTrue(valid("{\"type\":\"array\",\"items\":false}", "[]"));
        assertFalse(valid("{\"type\":\"array\",\"items\":false}", "[1]"));
    }

    @Test
    void shouldAcceptAllInstancesForTrueSubschema()
    {
        assertTrue(valid("{\"type\":\"array\",\"items\":true}", "[1,\"x\",null]"));
    }

    @Test
    void shouldRejectUnknownTypeName()
    {
        assertThrows(IllegalArgumentException.class, () -> JsonSchema.of("{\"type\":\"bogus\"}"));
    }

    @Test
    void shouldValidateStructuralConst()
    {
        String schema = "{\"const\":{\"a\":1,\"b\":[2,3]}}";
        assertTrue(valid(schema, "{\"b\":[2,3],\"a\":1}"));
        assertFalse(valid(schema, "{\"a\":1,\"b\":[3,2]}"));
        assertFalse(valid(schema, "{\"a\":1}"));
    }

    @Test
    void shouldTreatFormatAsAnnotationNotAssertion()
    {
        String schema = "{\"type\":\"string\",\"format\":\"email\"}";
        assertTrue(valid(schema, "\"not-an-email\""));
        assertTrue(valid(schema, "\"a@b.com\""));
        assertFalse(valid(schema, "5"));
    }

    @Test
    void shouldValidateStructuralEnum()
    {
        String schema = "{\"enum\":[{\"a\":1},[1,2],\"x\"]}";
        assertTrue(valid(schema, "{\"a\":1}"));
        assertTrue(valid(schema, "[1,2]"));
        assertTrue(valid(schema, "\"x\""));
        assertFalse(valid(schema, "{\"a\":2}"));
        assertFalse(valid(schema, "[2,1]"));
    }

    private static boolean valid(
        String schema,
        String instance)
    {
        // trailing space terminates a top-level number for the resumable parser,
        // which otherwise treats end-of-input mid-number as awaiting more bytes
        return JsonSchema.of(schema).validate(parserFor(instance + " "));
    }

    private static JsonParser parserFor(
        String text)
    {
        byte[] bytes = text.getBytes(UTF_8);
        DirectBufferInputStreamEx in = new DirectBufferInputStreamEx();
        in.wrap(new UnsafeBuffer(bytes), 0, bytes.length);
        return StreamingJson.createParser(in);
    }
}
