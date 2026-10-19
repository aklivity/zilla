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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

/**
 * A schema referencing one of the nine {@code google.protobuf} well-known wrapper types by name need not
 * declare or import it — {@link ProtobufSchema.Builder#build()} auto-registers each one's fixed single-field
 * shape, unless the schema already declares that name itself.
 */
public class ProtobufWellKnownTypesTest
{
    @Test
    public void shouldAutoRegisterWrapperMessages()
    {
        ProtobufSchema schema = Protobuf.schema().build();

        ProtobufMessage stringValue = schema.message("google.protobuf.StringValue");
        assertNotNull(stringValue);
        assertEquals(ProtobufType.STRING, stringValue.field(1).type());
        assertEquals("value", stringValue.field(1).name());

        assertEquals(ProtobufType.BYTES, schema.message("google.protobuf.BytesValue").field(1).type());
        assertEquals(ProtobufType.BOOL, schema.message("google.protobuf.BoolValue").field(1).type());
        assertEquals(ProtobufType.INT32, schema.message("google.protobuf.Int32Value").field(1).type());
        assertEquals(ProtobufType.UINT32, schema.message("google.protobuf.UInt32Value").field(1).type());
        assertEquals(ProtobufType.INT64, schema.message("google.protobuf.Int64Value").field(1).type());
        assertEquals(ProtobufType.UINT64, schema.message("google.protobuf.UInt64Value").field(1).type());
        assertEquals(ProtobufType.FLOAT, schema.message("google.protobuf.FloatValue").field(1).type());
        assertEquals(ProtobufType.DOUBLE, schema.message("google.protobuf.DoubleValue").field(1).type());
    }

    @Test
    public void shouldPreferExplicitlyDeclaredWrapperMessage()
    {
        ProtobufMessage declared = ProtobufMessage.builder("google.protobuf.StringValue")
            .field(ProtobufField.builder().number(1).name("value").type(ProtobufType.STRING).build())
            .build();

        ProtobufSchema schema = Protobuf.schema()
            .message(declared)
            .build();

        assertSame(declared, schema.message("google.protobuf.StringValue"));
    }

    @Test
    public void shouldNotAffectUnrelatedMessages()
    {
        ProtobufSchema schema = Protobuf.schema()
            .message(ProtobufMessage.builder("Person")
                .field(ProtobufField.builder().number(1).name("name").type(ProtobufType.STRING).build())
                .build())
            .build();

        assertNotSame(schema.message("Person"), schema.message("google.protobuf.StringValue"));
        assertEquals("Person", schema.message("Person").name());
    }

    @Test
    public void shouldResolveAndRoundTripUndeclaredWrapperFieldOnWireAlone()
    {
        ProtobufSchema schema = Protobuf.schema()
            .message(ProtobufMessage.builder("Msg")
                .field(ProtobufField.builder().number(1).name("note").type(ProtobufType.MESSAGE)
                    .typeName("google.protobuf.StringValue").build())
                .build())
            .build();

        assertNotNull(schema.message("Msg").field(1).message(),
            "field.message() should resolve the auto-registered wrapper without any explicit declaration");

        byte[] original = wire(g ->
        {
            g.startMessage(1, 0);
            g.writeString(1, "hello");
            g.endMessage();
        });

        MutableDirectBufferEx out = new UnsafeBufferEx(new byte[8192]);
        ProtobufGenerator generator = Protobuf.generator().wrap(out, 0, out.capacity());
        ProtobufPipeline pipeline = Protobuf.stream(Protobuf.parser(schema, "Msg"))
            .into(ProtobufSink.of(generator, schema, "Msg"));
        pipeline.reset();

        assertEquals(ProtobufPipeline.Status.COMPLETED,
            pipeline.transform(new UnsafeBufferEx(original), 0, original.length));

        byte[] roundTripped = new byte[generator.length()];
        out.getBytes(0, roundTripped);
        assertArrayEquals(original, roundTripped);
    }

    private static byte[] wire(
        Consumer<ProtobufGenerator> body)
    {
        MutableDirectBufferEx buffer = new UnsafeBufferEx(new byte[8192]);
        ProtobufGenerator generator = Protobuf.generator().wrap(buffer, 0, buffer.capacity());
        body.accept(generator);
        byte[] bytes = new byte[generator.length()];
        buffer.getBytes(0, bytes);
        return bytes;
    }
}
