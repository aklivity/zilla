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
package io.aklivity.zilla.runtime.common.protobuf.internal;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.function.Consumer;

import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.protobuf.Protobuf;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufSchema;
import io.aklivity.zilla.runtime.common.protobuf.ProtobufWireType;

public class ProtobufSchemaCompilerTest
{
    private static final int LABEL_OPTIONAL = 1;
    private static final int LABEL_REPEATED = 3;
    private static final int TYPE_INT32 = 5;
    private static final int TYPE_STRING = 9;
    private static final int TYPE_MESSAGE = 11;
    private static final int TYPE_ENUM = 14;

    @Test
    public void shouldCompileDescriptorSetAndResolveNames()
    {
        byte[] descriptorSet = personDescriptorSet();
        MutableDirectBuffer buffer = new UnsafeBuffer(descriptorSet);

        ProtobufSchema schema = Protobuf.schema(buffer, 0, descriptorSet.length);

        assertNotNull(schema.message("test.Person"));
        assertNotNull(schema.message("test.Person.Address"));
        assertNotNull(schema.enumeration("test.Person.Color"));
        assertEquals("emails", schema.message("test.Person").field(3).jsonName());
        assertEquals("test.Person.Address", schema.message("test.Person").field(4).typeName());
        assertTrue(schema.message("test.Person").field(3).repeated());
    }

    @Test
    public void shouldCanonicalizeThroughCompiledSchema()
    {
        byte[] descriptorSet = personDescriptorSet();
        MutableDirectBuffer descriptors = new UnsafeBuffer(descriptorSet);
        ProtobufSchema schema = Protobuf.schema(descriptors, 0, descriptorSet.length);

        byte[] address = encode(g ->
        {
            g.writeTag(1, ProtobufWireType.LEN);
            g.writeBytes("Zion".getBytes(UTF_8));
        });
        byte[] input = encode(w ->
        {
            w.writeTag(2, ProtobufWireType.VARINT);
            w.writeVarint64(1);
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes("Neo".getBytes(UTF_8));
            w.writeTag(3, ProtobufWireType.LEN);
            w.writeBytes("a".getBytes(UTF_8));
            w.writeTag(3, ProtobufWireType.LEN);
            w.writeBytes("b".getBytes(UTF_8));
            w.writeTag(4, ProtobufWireType.LEN);
            w.writeBytes(address);
            w.writeTag(5, ProtobufWireType.VARINT);
            w.writeVarint64(1);
        });

        byte[] once = canonicalize(schema, "test.Person", input);
        byte[] twice = canonicalize(schema, "test.Person", once);
        assertArrayEquals(once, twice);
    }

    @Test
    public void shouldCompileMapsOneofPackedAndProto3Optional()
    {
        byte[] descriptorSet = boxDescriptorSet();
        MutableDirectBuffer descriptors = new UnsafeBuffer(descriptorSet);
        ProtobufSchema schema = Protobuf.schema(descriptors, 0, descriptorSet.length);

        assertTrue(schema.message("test.Box.LabelsEntry").mapEntry());
        assertEquals("kind", schema.message("test.Box").field(1).oneofName());
        assertTrue(schema.message("test.Box").field(3).packed());
        assertTrue(schema.message("test.Box").field(5).proto3Optional());

        byte[] entry = encode(e ->
        {
            e.writeTag(1, ProtobufWireType.LEN);
            e.writeBytes("k".getBytes(UTF_8));
            e.writeTag(2, ProtobufWireType.VARINT);
            e.writeVarint64(7);
        });
        byte[] input = encode(w ->
        {
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes("hi".getBytes(UTF_8));
            w.writeTag(3, ProtobufWireType.VARINT);
            w.writeVarint64(1);
            w.writeTag(3, ProtobufWireType.VARINT);
            w.writeVarint64(2);
            w.writeTag(4, ProtobufWireType.LEN);
            w.writeBytes(entry);
            w.writeTag(5, ProtobufWireType.LEN);
            w.writeBytes("n".getBytes(UTF_8));
        });

        byte[] once = canonicalize(schema, "test.Box", input);
        byte[] twice = canonicalize(schema, "test.Box", once);
        assertArrayEquals(once, twice);
    }

    private static byte[] canonicalize(
        ProtobufSchema schema,
        String messageName,
        byte[] input)
    {
        MutableDirectBuffer out = new UnsafeBuffer(new byte[4096]);
        ProtobufCanonicalizer canonicalizer = new ProtobufCanonicalizer(schema);
        int length = canonicalizer.canonicalize(messageName, new UnsafeBuffer(input), 0, input.length, out, 0);
        byte[] result = new byte[length];
        out.getBytes(0, result);
        return result;
    }

    private static byte[] boxDescriptorSet()
    {
        byte[] s = fieldWithOneof("s", 1, TYPE_STRING, 0);
        byte[] i = fieldWithOneof("i", 2, TYPE_INT32, 0);
        byte[] nums = fieldPacked("nums", 3, TYPE_INT32);
        byte[] labels = field("labels", 4, TYPE_MESSAGE, LABEL_REPEATED, ".test.Box.LabelsEntry");
        byte[] note = fieldOptional("note", 5, TYPE_STRING);
        byte[] labelsEntry = mapEntryMessage("LabelsEntry");

        byte[] box = encode(w ->
        {
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes("Box".getBytes(UTF_8));
            for (byte[] f : List.of(s, i, nums, labels, note))
            {
                w.writeTag(2, ProtobufWireType.LEN);
                w.writeBytes(f);
            }
            w.writeTag(3, ProtobufWireType.LEN);
            w.writeBytes(labelsEntry);
            w.writeTag(8, ProtobufWireType.LEN);
            w.writeBytes(oneofDecl("kind"));
        });
        return set(List.of(file("test", List.of(box), List.of())));
    }

    private static byte[] fieldWithOneof(
        String name,
        int number,
        int type,
        int oneofIndex)
    {
        return encode(w ->
        {
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes(name.getBytes(UTF_8));
            w.writeTag(3, ProtobufWireType.VARINT);
            w.writeVarint64(number);
            w.writeTag(4, ProtobufWireType.VARINT);
            w.writeVarint64(LABEL_OPTIONAL);
            w.writeTag(5, ProtobufWireType.VARINT);
            w.writeVarint64(type);
            w.writeTag(9, ProtobufWireType.VARINT);
            w.writeVarint64(oneofIndex);
        });
    }

    private static byte[] fieldPacked(
        String name,
        int number,
        int type)
    {
        byte[] options = encode(w ->
        {
            w.writeTag(2, ProtobufWireType.VARINT);
            w.writeVarint64(1);
        });
        return encode(w ->
        {
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes(name.getBytes(UTF_8));
            w.writeTag(3, ProtobufWireType.VARINT);
            w.writeVarint64(number);
            w.writeTag(4, ProtobufWireType.VARINT);
            w.writeVarint64(LABEL_REPEATED);
            w.writeTag(5, ProtobufWireType.VARINT);
            w.writeVarint64(type);
            w.writeTag(8, ProtobufWireType.LEN);
            w.writeBytes(options);
        });
    }

    private static byte[] fieldOptional(
        String name,
        int number,
        int type)
    {
        return encode(w ->
        {
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes(name.getBytes(UTF_8));
            w.writeTag(3, ProtobufWireType.VARINT);
            w.writeVarint64(number);
            w.writeTag(4, ProtobufWireType.VARINT);
            w.writeVarint64(LABEL_OPTIONAL);
            w.writeTag(5, ProtobufWireType.VARINT);
            w.writeVarint64(type);
            w.writeTag(17, ProtobufWireType.VARINT);
            w.writeVarint64(1);
        });
    }

    private static byte[] oneofDecl(
        String name)
    {
        return encode(w ->
        {
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes(name.getBytes(UTF_8));
        });
    }

    private static byte[] mapEntryMessage(
        String name)
    {
        byte[] key = field("key", 1, TYPE_STRING, LABEL_OPTIONAL, null);
        byte[] value = field("value", 2, TYPE_INT32, LABEL_OPTIONAL, null);
        byte[] options = encode(w ->
        {
            w.writeTag(7, ProtobufWireType.VARINT);
            w.writeVarint64(1);
        });
        return encode(w ->
        {
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes(name.getBytes(UTF_8));
            w.writeTag(2, ProtobufWireType.LEN);
            w.writeBytes(key);
            w.writeTag(2, ProtobufWireType.LEN);
            w.writeBytes(value);
            w.writeTag(7, ProtobufWireType.LEN);
            w.writeBytes(options);
        });
    }

    private static byte[] personDescriptorSet()
    {
        byte[] name = field("name", 1, TYPE_STRING, LABEL_OPTIONAL, null);
        byte[] id = field("id", 2, TYPE_INT32, LABEL_OPTIONAL, null);
        byte[] emails = field("emails", 3, TYPE_STRING, LABEL_REPEATED, null);
        byte[] home = field("home", 4, TYPE_MESSAGE, LABEL_OPTIONAL, ".test.Person.Address");
        byte[] color = field("color", 5, TYPE_ENUM, LABEL_OPTIONAL, ".test.Person.Color");

        byte[] address = message("Address", List.of(field("city", 1, TYPE_STRING, LABEL_OPTIONAL, null)),
            List.of(), List.of());
        byte[] colorEnum = enumType("Color", List.of(enumValue("RED", 0), enumValue("GREEN", 1)));

        byte[] person = message("Person", List.of(name, id, emails, home, color),
            List.of(address), List.of(colorEnum));
        byte[] file = file("test", List.of(person), List.of());
        return set(List.of(file));
    }

    private static byte[] field(
        String name,
        int number,
        int type,
        int label,
        String typeName)
    {
        return encode(w ->
        {
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes(name.getBytes(UTF_8));
            w.writeTag(3, ProtobufWireType.VARINT);
            w.writeVarint64(number);
            w.writeTag(4, ProtobufWireType.VARINT);
            w.writeVarint64(label);
            w.writeTag(5, ProtobufWireType.VARINT);
            w.writeVarint64(type);
            if (typeName != null)
            {
                w.writeTag(6, ProtobufWireType.LEN);
                w.writeBytes(typeName.getBytes(UTF_8));
            }
        });
    }

    private static byte[] message(
        String name,
        List<byte[]> fields,
        List<byte[]> nested,
        List<byte[]> enums)
    {
        return encode(w ->
        {
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes(name.getBytes(UTF_8));
            for (byte[] field : fields)
            {
                w.writeTag(2, ProtobufWireType.LEN);
                w.writeBytes(field);
            }
            for (byte[] type : nested)
            {
                w.writeTag(3, ProtobufWireType.LEN);
                w.writeBytes(type);
            }
            for (byte[] type : enums)
            {
                w.writeTag(4, ProtobufWireType.LEN);
                w.writeBytes(type);
            }
        });
    }

    private static byte[] enumType(
        String name,
        List<byte[]> values)
    {
        return encode(w ->
        {
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes(name.getBytes(UTF_8));
            for (byte[] value : values)
            {
                w.writeTag(2, ProtobufWireType.LEN);
                w.writeBytes(value);
            }
        });
    }

    private static byte[] enumValue(
        String name,
        int number)
    {
        return encode(w ->
        {
            w.writeTag(1, ProtobufWireType.LEN);
            w.writeBytes(name.getBytes(UTF_8));
            w.writeTag(2, ProtobufWireType.VARINT);
            w.writeVarint64(number);
        });
    }

    private static byte[] file(
        String packageName,
        List<byte[]> messages,
        List<byte[]> enums)
    {
        return encode(w ->
        {
            w.writeTag(2, ProtobufWireType.LEN);
            w.writeBytes(packageName.getBytes(UTF_8));
            for (byte[] type : messages)
            {
                w.writeTag(4, ProtobufWireType.LEN);
                w.writeBytes(type);
            }
            for (byte[] type : enums)
            {
                w.writeTag(5, ProtobufWireType.LEN);
                w.writeBytes(type);
            }
        });
    }

    private static byte[] set(
        List<byte[]> files)
    {
        return encode(w ->
        {
            for (byte[] file : files)
            {
                w.writeTag(1, ProtobufWireType.LEN);
                w.writeBytes(file);
            }
        });
    }

    private static byte[] encode(
        Consumer<ProtobufWriter> body)
    {
        ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
        ProtobufWriter writer = new ProtobufWriter().wrap(buffer, 0);
        body.accept(writer);
        byte[] bytes = new byte[writer.length()];
        buffer.getBytes(0, bytes);
        return bytes;
    }
}
