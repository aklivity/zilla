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
package io.aklivity.zilla.runtime.common.protobuf;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

class ProtobufMessageMatchingPathsTest
{
    @Test
    void shouldMatchAnnotatedTopLevelField()
    {
        ProtobufSchema schema = Protobuf.schema(
            "syntax = \"proto3\";\n" +
            "message M {\n" +
            "  string email = 1 [(acme.kind) = \"A\"];\n" +
            "  string name = 2;\n" +
            "}\n");

        assertEquals(List.of("/email"), schema.message("M").matchingPaths(hasLabel("A")));
    }

    @Test
    void shouldReturnEmptyWhenNothingMatches()
    {
        ProtobufSchema schema = Protobuf.schema(
            "syntax = \"proto3\";\n" +
            "message M {\n" +
            "  string name = 1;\n" +
            "}\n");

        assertEquals(List.of(), schema.message("M").matchingPaths(hasLabel("A")));
    }

    @Test
    void shouldMatchNestedFieldUnderNonMatchingAncestor()
    {
        ProtobufSchema schema = Protobuf.schema(
            "syntax = \"proto3\";\n" +
            "message User {\n" +
            "  string ssn = 1 [(acme.kind) = \"A\"];\n" +
            "}\n" +
            "message M {\n" +
            "  User user = 1;\n" +
            "}\n");

        assertEquals(List.of("/user/ssn"), schema.message("M").matchingPaths(hasLabel("A")));
    }

    @Test
    void shouldNotStopDescentAtAMatchedAncestor()
    {
        ProtobufSchema schema = Protobuf.schema(
            "syntax = \"proto3\";\n" +
            "message User {\n" +
            "  string ssn = 1 [(acme.kind) = \"A\"];\n" +
            "  string name = 2;\n" +
            "}\n" +
            "message M {\n" +
            "  User user = 1 [(acme.kind) = \"A\"];\n" +
            "}\n");

        assertEquals(List.of("/user", "/user/ssn"), schema.message("M").matchingPaths(hasLabel("A")));
    }

    @Test
    void shouldInsertWildcardSegmentForRepeatedComposite()
    {
        ProtobufSchema schema = Protobuf.schema(
            "syntax = \"proto3\";\n" +
            "message Contact {\n" +
            "  string email = 1 [(acme.kind) = \"A\"];\n" +
            "}\n" +
            "message M {\n" +
            "  repeated Contact contacts = 1;\n" +
            "}\n");

        assertEquals(List.of("/contacts/-/email"), schema.message("M").matchingPaths(hasLabel("A")));
    }

    @Test
    void shouldMatchWithinMapEntryValue()
    {
        ProtobufSchema schema = Protobuf.schema(
            "syntax = \"proto3\";\n" +
            "message Contact {\n" +
            "  string email = 1 [(acme.kind) = \"A\"];\n" +
            "}\n" +
            "message M {\n" +
            "  map<string, Contact> by_id = 1;\n" +
            "}\n");

        // protobuf desugars a map into a repeated synthetic entry message with named key/value
        // fields, so the entry's "value" field contributes its own path segment here -- unlike
        // Avro/JSON Schema, whose map value type has no field wrapper of its own. Paths use the
        // field's declared proto name (not the derived proto3 JSON name).
        assertEquals(List.of("/by_id/-/value/email"), schema.message("M").matchingPaths(hasLabel("A")));
    }

    @Test
    void shouldTerminateOnRecursiveMessageWithoutInfiniteLoop()
    {
        ProtobufSchema schema = Protobuf.schema(
            "syntax = \"proto3\";\n" +
            "message Node {\n" +
            "  string secret = 1 [(acme.kind) = \"A\"];\n" +
            "  repeated Node children = 2;\n" +
            "}\n");

        assertEquals(List.of("/secret"), schema.message("Node").matchingPaths(hasLabel("A")));
    }

    private static Predicate<ProtobufField> hasLabel(
        String tag)
    {
        return field -> field.option("(acme.kind)") instanceof ProtobufConstant.TextValue text &&
            tag.equals(text.value());
    }
}
