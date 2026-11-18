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
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonSchema.Draft;

class JsonSchemaKeyAllocationTest
{
    private static final int PROPERTIES = 1_000;
    private static final int WARMUP_ROUNDS = 100;
    private static final int SAMPLES = 5;
    private static final int ROUNDS = 10;

    @Test
    void shouldNotCopyDeclaredKeysWhileReportingDiagnostics()
    {
        final JsonSchema schema = JsonSchema.of(objectSchema(), Draft.DRAFT_07);
        final String instance = objectInstance();

        final long silentBytes = allocated(schema, instance, null);
        final long reportingBytes = allocated(schema, instance, diagnostic ->
        {
        });

        final long excessPerProperty = (reportingBytes - silentBytes) / PROPERTIES;
        assertTrue(excessPerProperty <= 8,
            "reporting allocates " + excessPerProperty + " B per declared property");
    }

    private static long allocated(
        JsonSchema schema,
        String instance,
        Consumer<JsonSchemaDiagnostic> reporter)
    {
        for (int round = 0; round < WARMUP_ROUNDS; round++)
        {
            validate(schema, instance, reporter);
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
                assertTrue(validate(schema, instance, reporter));
            }
            least = Math.min(least, (threads.getThreadAllocatedBytes(threadId) - before) / ROUNDS);
        }
        return least;
    }

    private static boolean validate(
        JsonSchema schema,
        String instance,
        Consumer<JsonSchemaDiagnostic> reporter)
    {
        final JsonParserEx parser = JsonEx.createParser();
        final byte[] bytes = instance.getBytes(UTF_8);
        parser.wrap(new UnsafeBufferEx(bytes), 0, bytes.length, true);
        return reporter != null ? schema.validate(parser, reporter) : schema.validate(parser);
    }

    private static String objectSchema()
    {
        final StringBuilder schema = new StringBuilder("{\"type\":\"object\",\"properties\":{");
        for (int index = 0; index < PROPERTIES; index++)
        {
            schema.append(index == 0 ? "" : ",").append("\"k").append(index).append("\":{\"type\":\"string\"}");
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
