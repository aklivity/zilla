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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.management.ManagementFactory;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonSchema.Draft;

class JsonSchemaValidationAllocationTest
{
    private static final int DECLARED = 1_000;
    private static final int FEW = 10;
    private static final int MANY = 1_000;
    private static final int WARMUP_ROUNDS = 200;
    private static final int SAMPLES = 5;
    private static final int ROUNDS = 10;
    private static final int MAX_BYTES_PER_PROPERTY = 0;

    @Test
    void shouldNotAllocatePerPropertyForInlineSchemas()
    {
        assertNoPerPropertyAllocation(Draft.DRAFT_07, false);
    }

    @Test
    void shouldNotAllocatePerPropertyForReferencedSchemas()
    {
        assertNoPerPropertyAllocation(Draft.DRAFT_07, true);
    }

    @Test
    @Disabled("annotation drafts are evaluated on frames by a later change")
    void shouldNotAllocatePerPropertyForInlineSchemasWithAnnotations()
    {
        assertNoPerPropertyAllocation(Draft.DRAFT_2020_12, false);
    }

    @Test
    @Disabled("annotation drafts are evaluated on frames by a later change")
    void shouldNotAllocatePerPropertyForReferencedSchemasWithAnnotations()
    {
        assertNoPerPropertyAllocation(Draft.DRAFT_2020_12, true);
    }

    @Test
    void shouldNotAllocatePerArrayItem()
    {
        final JsonSchema schema = JsonSchema.of(
            "{\"type\":\"array\",\"items\":{\"type\":\"object\",\"required\":[\"id\"]," +
                "\"properties\":{\"id\":{\"type\":\"string\"}}}}", Draft.DRAFT_07);

        final long few = allocated(schema, items(FEW).getBytes(UTF_8));
        final long many = allocated(schema, items(MANY).getBytes(UTF_8));

        final long perItem = (many - few) / (MANY - FEW);
        assertTrue(perItem <= MAX_BYTES_PER_PROPERTY, "validation allocates " + perItem + " B per array item");
    }

    @Test
    void shouldNotAllocatePerPropertyForAnyOfSchemas()
    {
        assertNoAllocationPerProperty("{\"anyOf\":[{\"type\":\"integer\"},{\"type\":\"string\",\"minLength\":1}]}", "\"v\"");
    }

    @Test
    void shouldNotAllocatePerPropertyForOneOfSchemas()
    {
        assertNoAllocationPerProperty("{\"oneOf\":[{\"type\":\"string\",\"maxLength\":3},{\"type\":\"string\",\"minLength\":4}]}",
            "\"v\"");
    }

    @Test
    void shouldNotAllocatePerPropertyForAllOfSchemas()
    {
        assertNoAllocationPerProperty("{\"allOf\":[{\"type\":\"string\"},{\"minLength\":1},{\"maxLength\":9}]}", "\"v\"");
    }

    @Test
    void shouldNotAllocatePerPropertyForNotSchemas()
    {
        assertNoAllocationPerProperty("{\"not\":{\"type\":\"integer\"}}", "\"v\"");
    }

    @Test
    void shouldNotAllocatePerPropertyForConditionalSchemas()
    {
        assertNoAllocationPerProperty(
            "{\"if\":{\"type\":\"string\"},\"then\":{\"minLength\":1},\"else\":{\"type\":\"integer\"}}", "\"v\"");
    }

    @Test
    void shouldNotAllocatePerPropertyForNestedObjectsInCombinators()
    {
        assertNoAllocationPerProperty(
            "{\"oneOf\":[{\"type\":\"object\",\"required\":[\"id\"],\"properties\":{\"id\":{\"type\":\"string\"}}}," +
                "{\"type\":\"object\",\"required\":[\"name\"],\"properties\":{\"name\":{\"type\":\"string\"}}}]}",
            "{\"id\":\"v\"}");
    }

    @Test
    void shouldNotAllocatePerArrayItemOfOneOfObjects()
    {
        final JsonSchema schema = JsonSchema.of(
            "{\"type\":\"array\",\"items\":{\"oneOf\":[" +
                "{\"type\":\"object\",\"required\":[\"id\"],\"properties\":{\"id\":{\"type\":\"string\"}}}," +
                "{\"type\":\"object\",\"required\":[\"name\"],\"properties\":{\"name\":{\"type\":\"string\"}}}]}}",
            Draft.DRAFT_07);

        final long few = allocated(schema, oneOfItems(FEW).getBytes(UTF_8));
        final long many = allocated(schema, oneOfItems(MANY).getBytes(UTF_8));

        final long perItem = (many - few) / (MANY - FEW);
        assertTrue(perItem <= MAX_BYTES_PER_PROPERTY, "validation allocates " + perItem + " B per oneOf array item");
    }

    private static void assertNoAllocationPerProperty(
        String propertySchema,
        String propertyValue)
    {
        final StringBuilder schema = new StringBuilder("{\"type\":\"object\",\"properties\":{");
        for (int index = 0; index < DECLARED; index++)
        {
            schema.append(index == 0 ? "" : ",").append("\"k").append(index).append("\":").append(propertySchema);
        }
        final JsonSchema compiled = JsonSchema.of(schema.append("}}").toString(), Draft.DRAFT_07);

        final long few = allocated(compiled, objectOf(FEW, propertyValue).getBytes(UTF_8));
        final long many = allocated(compiled, objectOf(MANY, propertyValue).getBytes(UTF_8));

        final long perProperty = (many - few) / (MANY - FEW);
        assertTrue(perProperty <= MAX_BYTES_PER_PROPERTY,
            "validation allocates " + perProperty + " B per property of " + propertySchema);
    }

    private static String objectOf(
        int properties,
        String value)
    {
        final StringBuilder instance = new StringBuilder("{");
        for (int index = 0; index < properties; index++)
        {
            instance.append(index == 0 ? "" : ",").append("\"k").append(index).append("\":").append(value);
        }
        return instance.append("} ").toString();
    }

    private static String oneOfItems(
        int count)
    {
        final StringBuilder instance = new StringBuilder("[");
        for (int index = 0; index < count; index++)
        {
            instance.append(index == 0 ? "" : ",").append(index % 2 == 0 ? "{\"id\":\"v\"}" : "{\"name\":\"v\"}");
        }
        return instance.append("] ").toString();
    }

    private static void assertNoPerPropertyAllocation(
        Draft draft,
        boolean referenced)
    {
        final JsonSchema schema = JsonSchema.of(objectSchema(draft, referenced), draft);

        final long few = allocated(schema, objectInstance(FEW).getBytes(UTF_8));
        final long many = allocated(schema, objectInstance(MANY).getBytes(UTF_8));

        final long perProperty = (many - few) / (MANY - FEW);
        assertTrue(perProperty <= MAX_BYTES_PER_PROPERTY,
            "validation allocates " + perProperty + " B per declared property " + draft +
                (referenced ? " via $ref" : " inline"));
    }

    private static long allocated(
        JsonSchema schema,
        byte[] instance)
    {
        for (int round = 0; round < WARMUP_ROUNDS; round++)
        {
            validate(schema, instance);
        }

        final com.sun.management.ThreadMXBean threads =
            (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        final long threadId = Thread.currentThread().threadId();
        long least = Long.MAX_VALUE;
        for (int sample = 0; sample < SAMPLES; sample++)
        {
            final long before = threads.getThreadAllocatedBytes(threadId);
            for (int round = 0; round < ROUNDS; round++)
            {
                validate(schema, instance);
            }
            least = Math.min(least, (threads.getThreadAllocatedBytes(threadId) - before) / ROUNDS);
        }
        return least;
    }

    private static void validate(
        JsonSchema schema,
        byte[] bytes)
    {
        final JsonParserEx parser = schema.newParser(true, JsonEx.createParser());
        parser.wrap(new UnsafeBufferEx(bytes), 0, bytes.length, true);
        while (parser.hasNextEvent())
        {
            parser.nextEvent(JsonParserEx.Mode.STRUCTURED);
        }
    }

    private static String objectSchema(
        Draft draft,
        boolean referenced)
    {
        final String definitions = draft == Draft.DRAFT_07 ? "definitions" : "$defs";
        final StringBuilder schema = new StringBuilder("{\"type\":\"object\",\"properties\":{");
        for (int index = 0; index < DECLARED; index++)
        {
            schema.append(index == 0 ? "" : ",").append("\"k").append(index).append("\":")
                .append(referenced ? "{\"$ref\":\"#/" + definitions + "/text\"}" : "{\"type\":\"string\"}");
        }
        schema.append("}");
        if (referenced)
        {
            schema.append(",\"").append(definitions).append("\":{\"text\":{\"type\":\"string\"}}");
        }
        return schema.append("}").toString();
    }

    private static String objectInstance(
        int properties)
    {
        final StringBuilder instance = new StringBuilder("{");
        for (int index = 0; index < properties; index++)
        {
            instance.append(index == 0 ? "" : ",").append("\"k").append(index).append("\":\"v\"");
        }
        return instance.append("} ").toString();
    }

    private static String items(
        int count)
    {
        final StringBuilder instance = new StringBuilder("[");
        for (int index = 0; index < count; index++)
        {
            instance.append(index == 0 ? "" : ",").append("{\"id\":\"v\"}");
        }
        return instance.append("] ").toString();
    }
}
