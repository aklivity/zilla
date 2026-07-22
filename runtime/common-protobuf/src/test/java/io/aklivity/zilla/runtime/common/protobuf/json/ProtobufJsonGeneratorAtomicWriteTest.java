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
package io.aklivity.zilla.runtime.common.protobuf.json;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.protobuf.Protobuf;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufGenerator;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSchema;

// Drives ProtobufJsonGeneratorImpl directly (bypassing ProtobufTypedSinkImpl's binary-domain reserve()
// pre-check entirely, since it sizes against wire tag/varint bytes, not JSON text) so the window handed to
// the wrapped JsonGeneratorEx is exact. Regression coverage for #2040: ensureRoot()/start()/end()/
// emitDefaults() previously called json.writeStartObject()/writeKey()/writeEnd() unconditionally, with no
// room check of their own; a write that silently did nothing (once JsonGeneratorImpl itself stopped
// overflowing) would still have this adapter advance its own scope/depth bookkeeping as if it had happened,
// producing malformed JSON with no signal to the caller. Each compound write (a key immediately followed by
// its brace/bracket) now checks its exact total width before writing any of it, so a call either fully
// lands or touches nothing at all — never a dangling key with no value.
class ProtobufJsonGeneratorAtomicWriteTest
{
    private static final String SCHEMA =
        "syntax = \"proto3\";\n" +
        "message Inner { string v = 1; }\n" +
        "message Outer {\n" +
        "  Inner nested_field = 1;\n" +
        "  int32 a = 2;\n" +
        "}\n";

    @Test
    void shouldNotOpenNestedMessageWithoutRoomForItsKeyAndBrace()
    {
        ProtobufSchema schema = Protobuf.schema(SCHEMA);
        JsonGeneratorEx json = JsonEx.createGenerator();
        MutableDirectBufferEx out = new UnsafeBufferEx(new byte[64]);
        // root '{' (1) fits; the proto3 JSON name "nestedField":{ (14 + 1) does not with one byte held back
        ProtobufGenerator generator = ProtobufJson.generator(json, schema, "Outer").wrap(out, 0, 15);

        assertFalse(generator.startMessage(1, 0));
        // the root object legitimately opened (real, partial progress); the nested key was never begun
        assertEquals("{", drain(generator, out));
    }

    @Test
    void shouldOpenNestedMessageWhenExactRoomIsAvailable()
    {
        ProtobufSchema schema = Protobuf.schema(SCHEMA);
        JsonGeneratorEx json = JsonEx.createGenerator();
        MutableDirectBufferEx out = new UnsafeBufferEx(new byte[64]);
        // root '{' (1) + "nestedField": (14) + '{' (1) = 16, exactly
        ProtobufGenerator generator = ProtobufJson.generator(json, schema, "Outer").wrap(out, 0, 16);

        assertTrue(generator.startMessage(1, 0));
        assertEquals("{\"nestedField\":{", drain(generator, out));
    }

    @Test
    void shouldNotWriteDefaultKeyAndEmptyArrayWithoutRoom()
    {
        String schemaText =
            "syntax = \"proto3\";\n" +
            "message HasDefault {\n" +
            "  repeated int32 numbers = 1;\n" +
            "}\n";
        ProtobufSchema schema = Protobuf.schema(schemaText);
        JsonGeneratorEx json = JsonEx.createGenerator();
        MutableDirectBufferEx out = new UnsafeBufferEx(new byte[64]);
        // root '{' (1) + "numbers":[] default (12) + root '}' (1) = 14; 12 leaves no room for the default.
        // With no field explicitly written, flush() (matching ProtobufPipelineImpl's COMPLETED handling of
        // an empty message) is what opens the root and drives the defaults cascade — not startMessage(),
        // which is only ever called for a nested message field, never the root itself.
        ProtobufGenerator generator = ProtobufJson.generator(json, schema, "HasDefault",
            Map.of(ProtobufJson.INCLUDE_DEFAULTS, true)).wrap(out, 0, 12);

        assertFalse(generator.flush());
        // only the root brace landed; no dangling "numbers" key without its value
        assertEquals("{", drain(generator, out));
    }

    @Test
    void shouldWriteDefaultKeyAndEmptyArrayWhenExactRoomIsAvailable()
    {
        String schemaText =
            "syntax = \"proto3\";\n" +
            "message HasDefault {\n" +
            "  repeated int32 numbers = 1;\n" +
            "}\n";
        ProtobufSchema schema = Protobuf.schema(schemaText);
        JsonGeneratorEx json = JsonEx.createGenerator();
        MutableDirectBufferEx out = new UnsafeBufferEx(new byte[64]);
        ProtobufGenerator generator = ProtobufJson.generator(json, schema, "HasDefault",
            Map.of(ProtobufJson.INCLUDE_DEFAULTS, true)).wrap(out, 0, 14);

        assertTrue(generator.flush());
        assertEquals("{\"numbers\":[]}", drain(generator, out));
    }

    private static String drain(
        ProtobufGenerator generator,
        MutableDirectBufferEx buffer)
    {
        byte[] out = new byte[generator.length()];
        buffer.getBytes(0, out);
        return new String(out, UTF_8);
    }
}
