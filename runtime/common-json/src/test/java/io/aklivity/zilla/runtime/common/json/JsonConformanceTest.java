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

import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.stream.Stream;

import jakarta.json.JsonNumber;
import jakarta.json.JsonValue;
import jakarta.json.spi.JsonProvider;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

final class JsonConformanceTest
{
    private static final JsonProvider PROVIDER = JsonProvider.provider();

    private static final String[] VALID =
    {
        "{}",
        "[]",
        "{\"a\":1}",
        "[1,2,3]",
        "{\"nested\":{\"deep\":{\"deeper\":[1,[2,[3]]]}}}",
        "[0,-1,3.14,-2.5,1e10,-1.5E-3,123456789012345]",
        "{\"escaped\":\"a\\\"b\\\\c\\n\\t\\r\\b\\f\\/d\"}",
        "{\"unicode\":\"\\u00e9\\u4e2d\\ud83d\\ude00\"}",
        "{\"bools\":[true,false],\"null\":null}",
        "[{\"k\":\"v\"},{\"k\":\"w\"}]",
        "{\"empty_object\":{},\"empty_array\":[]}",
        "\"a plain string\"",
        "42",
        "true",
        "false",
        "null",
        "{\"big\":123456789012345678901234567890}",
    };

    private static final String[] INVALID =
    {
        "{",
        "[1,2",
        "{\"a\":}",
        "{\"a\" 1}",
        "[1 2]",
        "{'a':1}",
        "tru",
        "{\"a\":1,}",
        "[,]",
        "\"unterminated",
    };

    @TestFactory
    Stream<DynamicTest> shouldRoundTripValidDocuments()
    {
        return Stream.of(VALID)
            .map(json -> DynamicTest.dynamicTest(json, () ->
            {
                JsonValue first = PROVIDER.createReader(new StringReader(json)).readValue();
                StringWriter writer = new StringWriter();
                PROVIDER.createWriter(writer).write(first);
                JsonValue second = PROVIDER.createReader(new StringReader(writer.toString())).readValue();
                assertEquals(first, second, json);
            }));
    }

    @TestFactory
    Stream<DynamicTest> shouldReadBareTopLevelNumberAtEndOfInput()
    {
        return Stream.of("42", "-1", "3.14", "-1.5E-3", "0", "123456789012345")
            .map(json -> DynamicTest.dynamicTest(json, () ->
            {
                JsonValue value = PROVIDER.createReader(new StringReader(json)).readValue();
                assertEquals(new BigDecimal(json), ((JsonNumber) value).bigDecimalValue(), json);
            }));
    }

    @TestFactory
    Stream<DynamicTest> shouldRejectInvalidDocuments()
    {
        return Stream.of(INVALID)
            .map(json -> DynamicTest.dynamicTest(json, () ->
                assertThrows(RuntimeException.class, () ->
                    PROVIDER.createReader(new StringReader(json)).readValue(), json)));
    }
}
