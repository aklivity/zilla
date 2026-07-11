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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.StringReader;
import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonValue;

import org.junit.jupiter.api.Test;

class JsonPathTest
{
    @Test
    void shouldMatchRootOnly()
    {
        assertEquals(List.of(""), matches("$", "{\"a\":1}"));
    }

    @Test
    void shouldMatchTopLevelProperty()
    {
        assertEquals(List.of("/name"), matches("$.name", "{\"name\":\"x\",\"other\":2}"));
    }

    @Test
    void shouldMatchNestedProperty()
    {
        assertEquals(List.of("/a/b"), matches("$.a.b", "{\"a\":{\"b\":1,\"c\":2}}"));
    }

    @Test
    void shouldNotMatchMissingProperty()
    {
        assertEquals(List.of(), matches("$.missing", "{\"a\":1}"));
    }

    @Test
    void shouldMatchQuotedBracketPropertyContainingSlash()
    {
        assertEquals(List.of("/paths/~1pets"), matches("$.paths['/pets']", "{\"paths\":{\"/pets\":{}}}"));
    }

    @Test
    void shouldMatchDoubleQuotedBracketProperty()
    {
        assertEquals(List.of("/a"), matches("$[\"a\"]", "{\"a\":1}"));
    }

    @Test
    void shouldMatchArrayIndex()
    {
        assertEquals(List.of("/items/1"), matches("$.items[1]", "{\"items\":[\"a\",\"b\",\"c\"]}"));
    }

    @Test
    void shouldMatchNegativeArrayIndexFromEnd()
    {
        assertEquals(List.of("/items/2"), matches("$.items[-1]", "{\"items\":[\"a\",\"b\",\"c\"]}"));
    }

    @Test
    void shouldNotMatchOutOfBoundsArrayIndex()
    {
        assertEquals(List.of(), matches("$.items[5]", "{\"items\":[\"a\",\"b\",\"c\"]}"));
    }

    @Test
    void shouldMatchObjectWildcardTopLevel()
    {
        assertEquals(List.of("/a", "/b"), matches("$.*", "{\"a\":1,\"b\":2}"));
    }

    @Test
    void shouldMatchArrayWildcard()
    {
        assertEquals(List.of("/items/0", "/items/1"), matches("$.items[*]", "{\"items\":[10,20]}"));
    }

    @Test
    void shouldMatchBracketWildcard()
    {
        assertEquals(List.of("/a", "/b"), matches("$[*]", "{\"a\":1,\"b\":2}"));
    }

    @Test
    void shouldMatchChainedSegmentsAcrossWildcardAndBracket()
    {
        assertEquals(List.of("/paths/~1pets/get/parameters/0/name", "/paths/~1pets/get/parameters/1/name"),
            matches("$.paths['/pets'].get.parameters[*].name",
                "{\"paths\":{\"/pets\":{\"get\":{\"parameters\":" +
                    "[{\"name\":\"limit\"},{\"name\":\"offset\"}]}}}}"));
    }

    @Test
    void shouldStopDescendingIntoScalarWhenDeeperSegmentRemains()
    {
        assertEquals(List.of(), matches("$.a.b", "{\"a\":5}"));
    }

    @Test
    void shouldRejectExpressionNotStartingWithRoot()
    {
        assertThrows(IllegalArgumentException.class, () -> JsonPath.compile("a.b"));
    }

    @Test
    void shouldRejectUnterminatedBracket()
    {
        assertThrows(IllegalArgumentException.class, () -> JsonPath.compile("$.items[0"));
    }

    @Test
    void shouldRejectUnterminatedQuotedBracket()
    {
        assertThrows(IllegalArgumentException.class, () -> JsonPath.compile("$['a"));
    }

    private static List<String> matches(
        String expression,
        String document)
    {
        JsonValue root = Json.createReader(new StringReader(document)).readValue();
        return JsonPath.compile(expression).matches(root);
    }
}
