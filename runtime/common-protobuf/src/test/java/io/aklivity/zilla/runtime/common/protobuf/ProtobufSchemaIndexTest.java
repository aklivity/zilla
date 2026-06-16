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
package io.aklivity.zilla.runtime.common.protobuf;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

public class ProtobufSchemaIndexTest
{
    private static final String NESTED =
        "syntax = \"proto3\";\n" +
        "package io.aklivity.examples.clients.proto;\n" +
        "message SimpleMessage {\n" +
        "  string content = 1;\n" +
        "  message DeviceMessage2 {\n" +
        "    int32 id = 1;\n" +
        "    message DeviceMessage6 { int32 id = 1; }\n" +
        "  }\n" +
        "  DeviceMessage2 device = 3;\n" +
        "}\n" +
        "message DemoMessage {\n" +
        "  string status = 1;\n" +
        "  message DeviceMessage { int32 id = 1; }\n" +
        "  message DeviceMessage1 { int32 id = 1; }\n" +
        "  message SimpleMessage { string content = 1; }\n" +
        "}\n";

    @Test
    public void shouldResolveTopLevelIndexes()
    {
        ProtobufSchema schema = Protobuf.schema(NESTED);

        assertArrayEquals(new int[]{0}, schema.messageIndexes("SimpleMessage"));
        assertArrayEquals(new int[]{1}, schema.messageIndexes("DemoMessage"));
    }

    @Test
    public void shouldResolveNestedIndexes()
    {
        ProtobufSchema schema = Protobuf.schema(NESTED);

        assertArrayEquals(new int[]{1, 2}, schema.messageIndexes("DemoMessage.SimpleMessage"));
        assertArrayEquals(new int[]{0, 0, 0}, schema.messageIndexes("SimpleMessage.DeviceMessage2.DeviceMessage6"));
    }

    @Test
    public void shouldResolveMessageByIndexes()
    {
        ProtobufSchema schema = Protobuf.schema(NESTED);

        assertEquals("io.aklivity.examples.clients.proto.SimpleMessage",
            schema.messageByIndexes(new int[]{0}).name());
        assertEquals("io.aklivity.examples.clients.proto.DemoMessage.SimpleMessage",
            schema.messageByIndexes(new int[]{1, 2}).name());
    }

    @Test
    public void shouldOrderExplicitNestedBeforeSyntheticMapEntry()
    {
        ProtobufSchema schema = Protobuf.schema(
            "syntax = \"proto3\";\n" +
            "package t;\n" +
            "message M {\n" +
            "  map<string, int32> f = 1;\n" +
            "  message N { int32 id = 1; }\n" +
            "}\n");

        assertArrayEquals(new int[]{0, 0}, schema.messageIndexes("M.N"));
        assertArrayEquals(new int[]{0, 1}, schema.messageIndexes("M.FEntry"));
    }

    @Test
    public void shouldReturnNullForUnknownRecordOrIndexes()
    {
        ProtobufSchema schema = Protobuf.schema(NESTED);

        assertNull(schema.messageIndexes("Missing"));
        assertNull(schema.messageByIndexes(new int[]{9}));
    }
}
