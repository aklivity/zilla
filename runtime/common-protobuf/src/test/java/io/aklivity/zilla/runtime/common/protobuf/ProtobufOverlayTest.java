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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;

import jakarta.json.Json;
import jakarta.json.JsonValue;

import org.junit.jupiter.api.Test;

class ProtobufOverlayTest
{
    private static final String PROTO =
        "syntax = \"proto3\";\n" +
        "package test;\n" +
        "message Req { string id = 1; }\n" +
        "message Res { string id = 1; }\n" +
        "message Other { string email = 1; string name = 2; }\n" +
        "service Greeter {\n" +
        "  rpc SayHello (Req) returns (Res) {\n" +
        "    option (acme.doc) = { title: \"Say Hello\" };\n" +
        "  }\n" +
        "  rpc StreamHello (Req) returns (Res);\n" +
        "}\n" +
        "service Other1 {\n" +
        "  rpc First (Req) returns (Res);\n" +
        "  rpc Second (Req) returns (Res);\n" +
        "}\n";

    @Test
    void shouldMergeOptionsOntoMatchedMethod()
    {
        ProtobufSchema schema = Protobuf.schema(PROTO);
        ProtobufOverlay overlay = ProtobufOverlay.of(read(
            "[{\"method\":\"test.Greeter/SayHello\"," +
                "\"options\":{\"acme.doc\":{\"tags\":[\"public\"]}}}]"));

        ProtobufSchema result = overlay.apply(schema);

        assertEquals(
            "{\"acme.doc\":{\"title\":\"Say Hello\",\"tags\":[\"public\"]}}",
            result.service("test.Greeter").method("SayHello").options().toString());
    }

    @Test
    void shouldMergeOptionsOntoMatchedFieldExactReference()
    {
        ProtobufSchema schema = Protobuf.schema(PROTO);
        ProtobufOverlay overlay = ProtobufOverlay.of(read(
            "[{\"field\":\"test.Other.email\",\"options\":{\"acme\":{\"tags\":[\"EMAIL\"]}}}]"));

        ProtobufSchema result = overlay.apply(schema);

        assertEquals("{\"acme\":{\"tags\":[\"EMAIL\"]}}",
            result.message("test.Other").field("email").options().toString());
        assertEquals("{}", result.message("test.Other").field("name").options().toString());
    }

    @Test
    void shouldMatchMethodWildcardAcrossMultipleRpcs()
    {
        ProtobufSchema schema = Protobuf.schema(PROTO);
        ProtobufOverlay overlay = ProtobufOverlay.of(read(
            "[{\"method\":\"test.Other1/*\",\"options\":{\"acme\":{\"tagged\":true}}}]"));

        ProtobufSchema result = overlay.apply(schema);

        assertEquals("{\"acme\":{\"tagged\":true}}", result.service("test.Other1").method("First").options().toString());
        assertEquals("{\"acme\":{\"tagged\":true}}", result.service("test.Other1").method("Second").options().toString());
    }

    @Test
    void shouldApplyLaterEntryOnTopOfEarlierForSameMethod()
    {
        ProtobufSchema schema = Protobuf.schema(PROTO);
        ProtobufOverlay overlay = ProtobufOverlay.of(read(
            "[{\"method\":\"test.Greeter/SayHello\",\"options\":{\"acme.doc\":{\"title\":\"Overridden\"}}}," +
                "{\"method\":\"test.Greeter/SayHello\",\"options\":{\"acme.doc\":{\"description\":\"desc\"}}}]"));

        ProtobufSchema result = overlay.apply(schema);

        assertEquals(
            "{\"acme.doc\":{\"title\":\"Overridden\",\"description\":\"desc\"}}",
            result.service("test.Greeter").method("SayHello").options().toString());
    }

    @Test
    void shouldReturnSameSchemaInstanceWhenNothingMatches()
    {
        ProtobufSchema schema = Protobuf.schema(PROTO);
        ProtobufOverlay overlay = ProtobufOverlay.of(read(
            "[{\"method\":\"test.Missing/Method\",\"options\":{\"a\":1}}," +
                "{\"field\":\"test.Missing.field\",\"options\":{\"a\":1}}]"));

        ProtobufSchema result = overlay.apply(schema);

        assertSame(schema, result);
    }

    @Test
    void shouldLeaveUntouchedMessageAndServiceUnchangedByReference()
    {
        ProtobufSchema schema = Protobuf.schema(PROTO);
        ProtobufMessage untouchedMessage = schema.message("test.Req");
        ProtobufService untouchedService = schema.service("test.Other1");
        ProtobufOverlay overlay = ProtobufOverlay.of(read(
            "[{\"field\":\"test.Other.email\",\"options\":{\"acme\":{\"tags\":[\"EMAIL\"]}}}]"));

        ProtobufSchema result = overlay.apply(schema);

        assertSame(untouchedMessage, result.message("test.Req"));
        assertSame(untouchedService, result.service("test.Other1"));
    }

    @Test
    void shouldReuseOneOverlayAcrossMultipleSchemas()
    {
        ProtobufSchema matching = Protobuf.schema(PROTO);
        ProtobufSchema nonMatching = Protobuf.schema(
            "syntax = \"proto3\";\n" +
            "package other;\n" +
            "message M { string v = 1; }\n");
        ProtobufOverlay overlay = ProtobufOverlay.of(read(
            "[{\"method\":\"test.Greeter/SayHello\",\"options\":{\"acme.doc\":{\"title\":\"X\"}}}]"));

        ProtobufSchema matchedResult = overlay.apply(matching);
        ProtobufSchema unmatchedResult = overlay.apply(nonMatching);

        assertEquals("{\"acme.doc\":{\"title\":\"X\"}}",
            matchedResult.service("test.Greeter").method("SayHello").options().toString());
        assertSame(nonMatching, unmatchedResult);
    }

    @Test
    void shouldRejectEntryWithNeitherMethodNorField()
    {
        JsonValue document = read("[{\"options\":{\"a\":1}}]");

        assertThrows(IllegalArgumentException.class, () -> ProtobufOverlay.of(document));
    }

    @Test
    void shouldRejectEntryWithBothMethodAndField()
    {
        JsonValue document = read(
            "[{\"method\":\"test.Greeter/SayHello\",\"field\":\"test.Other.email\",\"options\":{\"a\":1}}]");

        assertThrows(IllegalArgumentException.class, () -> ProtobufOverlay.of(document));
    }

    @Test
    void shouldRejectEntryMissingOptions()
    {
        JsonValue document = read("[{\"method\":\"test.Greeter/SayHello\"}]");

        assertThrows(IllegalArgumentException.class, () -> ProtobufOverlay.of(document));
    }

    @Test
    void shouldExposeInlineOptionsWhenNoOverlayApplied()
    {
        ProtobufSchema schema = Protobuf.schema(PROTO);

        assertEquals("{\"title\":\"Say Hello\"}",
            schema.service("test.Greeter").method("SayHello").option("(acme.doc)").toJson().toString());
        assertTrue(schema.service("test.Greeter").method("SayHello").options().containsKey("acme.doc"));
        assertNull(schema.service("test.Greeter").method("StreamHello").option("(acme.doc)"));
    }

    private static JsonValue read(
        String text)
    {
        return Json.createReader(new StringReader(text)).readValue();
    }
}
