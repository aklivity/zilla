/*
 * Copyright 2021-2026 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.binding.kafka.internal.stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.function.Consumer;

import org.junit.Test;

import io.aklivity.zilla.runtime.binding.kafka.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaFilterFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaHeaderFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaKeyFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaSkip;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.message.MessageHeaderFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class KafkaRecordFilterTest
{
    private static final DirectBufferEx NO_HEADERS = new UnsafeBufferEx(new byte[0]);

    private final MutableDirectBufferEx filtersBuffer = new UnsafeBufferEx(new byte[4096]);
    private final MutableDirectBufferEx headersBuffer = new UnsafeBufferEx(new byte[1024]);

    @Test
    public void shouldMatchWhenNoFilters()
    {
        final KafkaRecordFilter filter = filter();

        assertTrue(filter.test(key("any"), NO_HEADERS, 0));
        assertTrue(filter.test(null, NO_HEADERS, 0));
    }

    @Test
    public void shouldMatchKey()
    {
        final KafkaRecordFilter filter = filter(f -> f.conditionsItem(c -> c.key(k -> key(k, "match"))));

        assertTrue(filter.test(key("match"), NO_HEADERS, 0));
        assertFalse(filter.test(key("other"), NO_HEADERS, 0));
        assertFalse(filter.test(key("matches"), NO_HEADERS, 0));
        assertFalse(filter.test(null, NO_HEADERS, 0));
    }

    @Test
    public void shouldMatchNullKey()
    {
        final KafkaRecordFilter filter = filter(f -> f.conditionsItem(c -> c.key(k -> key(k, null))));

        assertTrue(filter.test(null, NO_HEADERS, 0));
        assertFalse(filter.test(key("match"), NO_HEADERS, 0));
    }

    @Test
    public void shouldMatchHeader()
    {
        final KafkaRecordFilter filter = filter(f -> f.conditionsItem(c -> c.header(h -> header(h, "name", "value"))));

        assertTrue(filter.test(null, headers("other", "x", "name", "value"), 2));
        assertFalse(filter.test(null, headers("name", "different"), 1));
        assertFalse(filter.test(null, headers("other", "value"), 1));
        assertFalse(filter.test(null, NO_HEADERS, 0));
    }

    @Test
    public void shouldMatchHeaderWithNullValue()
    {
        final KafkaRecordFilter filter = filter(f -> f.conditionsItem(c -> c.header(h -> header(h, "name", null))));

        assertTrue(filter.test(null, headers("name", null), 1));
        assertFalse(filter.test(null, headers("name", "value"), 1));
    }

    @Test
    public void shouldMatchNotKey()
    {
        final KafkaRecordFilter filter = filter(f -> f.conditionsItem(c -> c.not(n -> n.condition(
            nc -> nc.key(k -> key(k, "skip"))))));

        assertTrue(filter.test(key("other"), NO_HEADERS, 0));
        assertTrue(filter.test(null, NO_HEADERS, 0));
        assertFalse(filter.test(key("skip"), NO_HEADERS, 0));
    }

    @Test
    public void shouldMatchNotHeader()
    {
        final KafkaRecordFilter filter = filter(f -> f.conditionsItem(c -> c.not(n -> n.condition(
            nc -> nc.header(h -> header(h, "name", "value"))))));

        assertTrue(filter.test(null, headers("name", "different"), 1));
        assertFalse(filter.test(null, headers("name", "value"), 1));
    }

    @Test
    public void shouldMatchDoubleNegation()
    {
        final KafkaRecordFilter filter = filter(f -> f.conditionsItem(c -> c.not(n -> n.condition(
            nc -> nc.not(nn -> nn.condition(nnc -> nnc.key(k -> key(k, "match"))))))));

        assertTrue(filter.test(key("match"), NO_HEADERS, 0));
        assertFalse(filter.test(key("other"), NO_HEADERS, 0));
    }

    @Test
    public void shouldMatchAllConditionsInFilter()
    {
        final KafkaRecordFilter filter = filter(f -> f
            .conditionsItem(c -> c.key(k -> key(k, "match")))
            .conditionsItem(c -> c.header(h -> header(h, "name", "value"))));

        assertTrue(filter.test(key("match"), headers("name", "value"), 1));
        assertFalse(filter.test(key("match"), headers("name", "different"), 1));
        assertFalse(filter.test(key("other"), headers("name", "value"), 1));
    }

    @Test
    public void shouldMatchAnyFilter()
    {
        final KafkaRecordFilter filter = filter(
            f -> f.conditionsItem(c -> c.key(k -> key(k, "first"))),
            f -> f.conditionsItem(c -> c.key(k -> key(k, "second"))));

        assertTrue(filter.test(key("first"), NO_HEADERS, 0));
        assertTrue(filter.test(key("second"), NO_HEADERS, 0));
        assertFalse(filter.test(key("third"), NO_HEADERS, 0));
    }

    @Test
    public void shouldMatchHeadersSequence()
    {
        final KafkaRecordFilter filter = filter(f -> f.conditionsItem(c -> c.headers(h -> h
            .nameLen(4)
            .name(bytes("name"), 0, 4)
            .valuesItem(v -> v.value(vv -> vv.length(2).value(bytes("v1"), 0, 2)))
            .valuesItem(v -> v.value(vv -> vv.length(2).value(bytes("v2"), 0, 2))))));

        assertTrue(filter.test(null, headers("name", "v1", "other", "x", "name", "v2"), 3));
        assertFalse(filter.test(null, headers("name", "v1"), 1));
        assertFalse(filter.test(null, headers("name", "v2", "name", "v1"), 2));
        assertFalse(filter.test(null, headers("name", "v1", "name", "v2", "name", "v3"), 3));
        assertFalse(filter.test(null, NO_HEADERS, 0));
    }

    @Test
    public void shouldMatchHeadersSequenceWithSkip()
    {
        final KafkaRecordFilter filter = filter(f -> f.conditionsItem(c -> c.headers(h -> h
            .nameLen(4)
            .name(bytes("name"), 0, 4)
            .valuesItem(v -> v.skip(s -> s.set(KafkaSkip.SKIP)))
            .valuesItem(v -> v.value(vv -> vv.length(2).value(bytes("v2"), 0, 2))))));

        assertTrue(filter.test(null, headers("name", "anything", "name", "v2"), 2));
        assertFalse(filter.test(null, headers("name", "anything", "name", "v3"), 2));
    }

    @Test
    public void shouldMatchHeadersSequenceWithSkipMany()
    {
        final KafkaRecordFilter filter = filter(f -> f.conditionsItem(c -> c.headers(h -> h
            .nameLen(4)
            .name(bytes("name"), 0, 4)
            .valuesItem(v -> v.value(vv -> vv.length(2).value(bytes("v1"), 0, 2)))
            .valuesItem(v -> v.skip(s -> s.set(KafkaSkip.SKIP_MANY))))));

        assertTrue(filter.test(null, headers("name", "v1", "name", "v2", "name", "v3"), 3));
        assertTrue(filter.test(null, headers("name", "v1", "name", "v2"), 2));
        assertFalse(filter.test(null, headers("name", "v1"), 1));
        assertFalse(filter.test(null, headers("name", "v0", "name", "v2"), 2));
    }

    @SafeVarargs
    private KafkaRecordFilter filter(
        Consumer<KafkaFilterFW.Builder>... filters)
    {
        final Array32FW.Builder<KafkaFilterFW.Builder, KafkaFilterFW> builder =
            new Array32FW.Builder<>(new KafkaFilterFW.Builder(), new KafkaFilterFW())
                .wrap(filtersBuffer, 0, filtersBuffer.capacity());

        for (Consumer<KafkaFilterFW.Builder> filter : filters)
        {
            builder.item(filter);
        }

        return new KafkaRecordFilter(builder.build());
    }

    private static KafkaKeyFW.Builder key(
        KafkaKeyFW.Builder builder,
        String value)
    {
        return value != null
            ? builder.length(value.length()).value(bytes(value), 0, value.length())
            : builder.length(-1).value((OctetsFW) null);
    }

    private static KafkaHeaderFW.Builder header(
        KafkaHeaderFW.Builder builder,
        String name,
        String value)
    {
        builder.nameLen(name.length()).name(bytes(name), 0, name.length());
        return value != null
            ? builder.valueLen(value.length()).value(bytes(value), 0, value.length())
            : builder.valueLen(-1).value((OctetsFW) null);
    }

    private static OctetsFW key(
        String value)
    {
        final DirectBufferEx buffer = bytes(value);
        return new OctetsFW().wrap(buffer, 0, buffer.capacity());
    }

    private DirectBufferEx headers(
        String... namesAndValues)
    {
        int limit = 0;
        for (int index = 0; index < namesAndValues.length; index += 2)
        {
            final String name = namesAndValues[index];
            final String value = namesAndValues[index + 1];

            final MessageHeaderFW.Builder builder = new MessageHeaderFW.Builder()
                .wrap(headersBuffer, limit, headersBuffer.capacity())
                .keyLength(name.length())
                .key(bytes(name), 0, name.length());

            if (value != null)
            {
                builder.valueLength(value.length()).value(bytes(value), 0, value.length());
            }
            else
            {
                builder.valueLength(-1).value((OctetsFW) null);
            }

            limit = builder.build().limit();
        }

        return new UnsafeBufferEx(headersBuffer, 0, limit);
    }

    private static DirectBufferEx bytes(
        String value)
    {
        return new UnsafeBufferEx(value.getBytes(UTF_8));
    }
}
