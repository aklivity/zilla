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
package io.aklivity.zilla.runtime.common.avro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

public class AvroTypeTest
{
    @Test
    public void shouldInspectRecord()
    {
        AvroType type = Avro.schema("""
            {"type":"record","name":"R","fields":[
            {"name":"id","type":"int"},
            {"name":"name","type":"string"}]}""").type();

        assertEquals(AvroKind.RECORD, type.kind());
        assertEquals("R", type.name());
        List<AvroField> fields = type.fields();
        assertEquals(2, fields.size());
        assertEquals("id", fields.get(0).name());
        assertEquals(AvroKind.INT, fields.get(0).type().kind());
        assertEquals("name", fields.get(1).name());
        assertEquals(AvroKind.STRING, fields.get(1).type().kind());
    }

    @Test
    public void shouldInspectArray()
    {
        AvroType type = Avro.schema("{\"type\":\"array\",\"items\":\"long\"}").type();
        assertEquals(AvroKind.ARRAY, type.kind());
        assertEquals(AvroKind.LONG, type.items().kind());
    }

    @Test
    public void shouldInspectMap()
    {
        AvroType type = Avro.schema("{\"type\":\"map\",\"values\":\"boolean\"}").type();
        assertEquals(AvroKind.MAP, type.kind());
        assertEquals(AvroKind.BOOLEAN, type.values().kind());
    }

    @Test
    public void shouldInspectUnion()
    {
        AvroType type = Avro.schema("[\"null\",\"string\"]").type();
        assertEquals(AvroKind.UNION, type.kind());
        List<AvroType> branches = type.branches();
        assertEquals(2, branches.size());
        assertEquals(AvroKind.NULL, branches.get(0).kind());
        assertEquals(AvroKind.STRING, branches.get(1).kind());
    }

    @Test
    public void shouldInspectEnum()
    {
        AvroType type = Avro.schema(
            "{\"type\":\"enum\",\"name\":\"Suit\",\"symbols\":[\"SPADES\",\"HEARTS\"]}").type();
        assertEquals(AvroKind.ENUM, type.kind());
        assertEquals("Suit", type.name());
        assertEquals(List.of("SPADES", "HEARTS"), type.symbols());
    }

    @Test
    public void shouldInspectFixed()
    {
        AvroType type = Avro.schema(
            "{\"type\":\"fixed\",\"name\":\"Hash\",\"size\":16,\"logicalType\":\"decimal\"}").type();
        assertEquals(AvroKind.FIXED, type.kind());
        assertEquals("Hash", type.name());
        assertEquals(16, type.size());
        assertEquals("decimal", type.logicalType());
    }

    @Test
    public void shouldInspectPrimitiveWithEmptyAccessors()
    {
        AvroType type = Avro.schema("\"int\"").type();
        assertEquals(AvroKind.INT, type.kind());
        assertNull(type.name());
        assertNull(type.logicalType());
        assertNull(type.items());
        assertNull(type.values());
        assertEquals(0, type.size());
        assertTrue(type.fields().isEmpty());
        assertTrue(type.branches().isEmpty());
        assertTrue(type.symbols().isEmpty());
    }

    @Test
    public void shouldTerminateOnRecursiveType()
    {
        AvroType type = Avro.schema("""
            {"type":"record","name":"Node","fields":[
            {"name":"next","type":["null","Node"]}]}""").type();

        assertEquals("Node", type.name());
        AvroType branch = type.fields().get(0).type().branches().get(1);
        // the recursive reference resolves to the same node, so traversal terminates by identity
        assertEquals(AvroKind.RECORD, branch.kind());
        assertSame(type, branch);
    }

    @Test
    public void shouldExposeBranchTypeAtUnionBranch()
    {
        AvroParser parser = Avro.parser(Avro.schema("[\"null\",\"string\"]"));
        // branch 1 (string) "x": 0x02 0x02 0x78
        parser.wrap(new UnsafeBuffer(new byte[] { 0x02, 0x02, 0x78 }), 0, 3);

        assertEquals(AvroEvent.START_MESSAGE, next(parser));
        assertEquals(AvroEvent.UNION_BRANCH, next(parser));
        assertEquals(AvroKind.STRING, parser.type().kind());
        assertEquals(AvroEvent.STRING, next(parser));
        assertEquals(AvroKind.STRING, parser.type().kind());
        assertEquals(AvroEvent.END_MESSAGE, next(parser));
        assertNull(parser.type());
    }

    @Test
    public void shouldExposeFieldAndRecordTypeWhileParsing()
    {
        AvroParser parser = Avro.parser(Avro.schema("""
            {"type":"record","name":"R","fields":[
            {"name":"id","type":"int"},
            {"name":"name","type":"string"}]}"""));
        parser.wrap(new UnsafeBuffer(new byte[] { 0x02, 0x04, 0x68, 0x69 }), 0, 4);

        assertEquals(AvroEvent.START_MESSAGE, next(parser));
        assertEquals(AvroKind.RECORD, parser.type().kind());
        assertEquals(AvroEvent.START_RECORD, next(parser));
        assertEquals("R", parser.type().name());
        assertEquals(AvroEvent.FIELD_NAME, next(parser));
        assertEquals(AvroKind.INT, parser.type().kind());
        assertEquals(AvroEvent.INT, next(parser));
        assertEquals(AvroKind.INT, parser.type().kind());
    }

    private static AvroEvent next(
        AvroParser parser)
    {
        assertTrue(parser.hasNext());
        return parser.nextEvent();
    }
}
