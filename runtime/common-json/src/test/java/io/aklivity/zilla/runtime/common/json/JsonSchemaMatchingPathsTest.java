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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.function.Predicate;

import jakarta.json.JsonString;
import jakarta.json.JsonValue;

import org.junit.jupiter.api.Test;

class JsonSchemaMatchingPathsTest
{
    @Test
    void shouldMatchAnnotatedTopLevelProperty()
    {
        JsonSchema schema = JsonSchema.of("""
            {"type":"object","properties":{
              "email":{"type":"string","x-labels":["A"]},
              "name":{"type":"string"}
            }}""");

        assertEquals(List.of("/email"), schema.matchingPaths(hasLabel("A")));
    }

    @Test
    void shouldReturnEmptyWhenNothingMatches()
    {
        JsonSchema schema = JsonSchema.of(
            "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"}}}");

        assertEquals(List.of(), schema.matchingPaths(hasLabel("A")));
    }

    @Test
    void shouldMatchNestedPropertyUnderNonMatchingAncestor()
    {
        JsonSchema schema = JsonSchema.of("""
            {"properties":{"user":{"type":"object","properties":{
              "ssn":{"type":"string","x-labels":["A"]}
            }}}}""");

        assertEquals(List.of("/user/ssn"), schema.matchingPaths(hasLabel("A")));
    }

    @Test
    void shouldNotStopDescentAtAMatchedAncestor()
    {
        JsonSchema schema = JsonSchema.of("""
            {"properties":{"user":{"type":"object","x-labels":["A"],"properties":{
              "ssn":{"type":"string","x-labels":["A"]},
              "name":{"type":"string"}
            }}}}""");

        assertEquals(List.of("/user", "/user/ssn"), schema.matchingPaths(hasLabel("A")));
    }

    @Test
    void shouldMatchWithinArrayItems()
    {
        JsonSchema schema = JsonSchema.of("""
            {"properties":{"contacts":{"type":"array","items":{"type":"object","properties":{
              "email":{"type":"string","x-labels":["A"]}
            }}}}}""");

        assertEquals(List.of("/contacts/-/email"), schema.matchingPaths(hasLabel("A")));
    }

    @Test
    void shouldMatchAcrossCombinatorBranches()
    {
        JsonSchema schema = JsonSchema.of("""
            {"oneOf":[
              {"properties":{"a":{"type":"string","x-labels":["A"]}}},
              {"properties":{"b":{"type":"string"}}}
            ]}""");

        assertEquals(List.of("/a"), schema.matchingPaths(hasLabel("A")));
    }

    @Test
    void shouldNotDescendIntoTupleItems()
    {
        JsonSchema schema = JsonSchema.of("""
            {"properties":{"t":{"type":"array","items":[
              {"type":"string","x-labels":["A"]}
            ]}}}""");

        assertTrue(schema.matchingPaths(matchAll()).stream().noneMatch(path -> path.startsWith("/t/")));
    }

    @Test
    void shouldNotExpandRef()
    {
        JsonSchema schema = JsonSchema.of("""
            {"$defs":{"Email":{"type":"string","x-labels":["A"]}},
             "type":"object","properties":{"contact":{"$ref":"#/$defs/Email"}}}""",
            JsonSchema.Draft.DRAFT_07);

        assertEquals(List.of(), schema.matchingPaths(hasLabel("A")));
    }

    private static Predicate<JsonSchema> matchAll()
    {
        return schema -> true;
    }

    private static Predicate<JsonSchema> hasLabel(
        String tag)
    {
        return schema ->
        {
            JsonValue tags = schema.attribute("x-labels");
            return tags != null && tags.getValueType() == JsonValue.ValueType.ARRAY &&
                tags.asJsonArray().stream().anyMatch(value -> value.getValueType() == JsonValue.ValueType.STRING &&
                    tag.equals(((JsonString) value).getString()));
        };
    }
}
