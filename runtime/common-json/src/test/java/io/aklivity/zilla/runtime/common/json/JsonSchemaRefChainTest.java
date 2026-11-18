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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.management.ManagementFactory;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonSchema.Draft;

class JsonSchemaRefChainTest
{
    private static final int PROPERTIES = 1_000;
    private static final int WARMUP_ROUNDS = 100;
    private static final int SAMPLES = 5;
    private static final int ROUNDS = 10;

    private static final String CHAINED_SCHEMA =
        "{\"definitions\":{\"a\":{\"$ref\":\"#/definitions/b\"},\"b\":{\"$ref\":\"#/definitions/c\"}," +
        "\"c\":{\"type\":\"string\"}},\"$ref\":\"#/definitions/a\"}";

    private static final String CYCLIC_SCHEMA =
        "{\"definitions\":{\"a\":{\"$ref\":\"#/definitions/b\"},\"b\":{\"$ref\":\"#/definitions/a\"}}," +
        "\"$ref\":\"#/definitions/a\"}";

    private static final String SELF_CYCLIC_SCHEMA =
        "{\"definitions\":{\"a\":{\"$ref\":\"#/definitions/a\"}},\"$ref\":\"#/definitions/a\"}";

    @Test
    void shouldValidateThroughChainOfReferences()
    {
        final JsonSchema schema = JsonSchema.of(CHAINED_SCHEMA, Draft.DRAFT_07);

        assertTrue(validate(schema, "\"text\" "));
        assertFalse(validate(schema, "42 "));
    }

    @Test
    void shouldRejectCyclicReferenceEveryTimeItIsUsed()
    {
        final JsonSchema schema = JsonSchema.of(CYCLIC_SCHEMA, Draft.DRAFT_07);

        for (int round = 0; round < 3; round++)
        {
            final IllegalStateException ex = assertThrows(IllegalStateException.class, () -> validate(schema, "\"text\" "));
            assertTrue(ex.getMessage().startsWith("cyclic $ref"), ex.getMessage());
        }
    }

    @Test
    void shouldRejectSelfReferencingReference()
    {
        final JsonSchema schema = JsonSchema.of(SELF_CYCLIC_SCHEMA, Draft.DRAFT_07);

        final IllegalStateException ex = assertThrows(IllegalStateException.class, () -> validate(schema, "\"text\" "));
        assertTrue(ex.getMessage().startsWith("cyclic $ref"), ex.getMessage());
    }

    @Test
    void shouldNotAllocateForReferencedPropertyBeyondInlinedProperty()
    {
        final JsonSchema referenced = JsonSchema.of(objectSchema("{\"$ref\":\"#/definitions/text\"}"), Draft.DRAFT_07);
        final JsonSchema inlined = JsonSchema.of(objectSchema("{\"type\":\"string\"}"), Draft.DRAFT_07);
        final String instance = objectInstance();

        final long referencedBytes = allocated(referenced, instance);
        final long inlinedBytes = allocated(inlined, instance);

        final long excessPerProperty = (referencedBytes - inlinedBytes) / PROPERTIES;
        assertTrue(excessPerProperty <= 8,
            "referenced property allocates " + excessPerProperty + " B more than an inlined property");
    }

    private static boolean validate(
        JsonSchema schema,
        String instance)
    {
        final JsonParserEx parser = JsonEx.createParser();
        final byte[] bytes = instance.getBytes(UTF_8);
        parser.wrap(new UnsafeBufferEx(bytes), 0, bytes.length, true);
        return schema.validate(parser);
    }

    private static long allocated(
        JsonSchema schema,
        String instance)
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
                assertTrue(validate(schema, instance));
            }
            least = Math.min(least, (threads.getThreadAllocatedBytes(threadId) - before) / ROUNDS);
        }
        return least;
    }

    private static String objectSchema(
        String property)
    {
        final StringBuilder schema = new StringBuilder(
            "{\"type\":\"object\",\"definitions\":{\"text\":{\"type\":\"string\"}},\"properties\":{");
        for (int index = 0; index < PROPERTIES; index++)
        {
            schema.append(index == 0 ? "" : ",").append("\"k").append(index).append("\":").append(property);
        }
        return schema.append("}}").toString();
    }

    private static String objectInstance()
    {
        final StringBuilder instance = new StringBuilder("{");
        for (int index = 0; index < PROPERTIES; index++)
        {
            instance.append(index == 0 ? "" : ",").append("\"k").append(index).append("\":\"v\"");
        }
        return instance.append("} ").toString();
    }
}
