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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.function.Consumer;

import org.agrona.collections.IntArrayList;
import org.junit.Test;

import io.aklivity.zilla.runtime.binding.kafka.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaFilterFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaHeaderFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaKeyFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaSkip;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

public class KafkaFilterTermsTest
{
    private final KafkaFilterTerms filterTerms = new KafkaFilterTerms();
    private final MutableDirectBufferEx filtersBuffer = new UnsafeBufferEx(new byte[4096]);
    private final IntArrayList terms = new IntArrayList();

    @Test
    public void shouldCollectHeaderTerm()
    {
        final boolean indexed = filterTerms.collect(
            filters(f -> f.conditionsItem(c -> c.header(h -> header(h, "name", "value")))), terms);

        assertTrue(indexed);
        assertEquals(1, terms.size());
        assertEquals(KafkaFilterTerms.hashHeader(octets("name"), octets("value")), terms.getInt(0));
    }

    @Test
    public void shouldCollectLastLiteralOfHeaders()
    {
        final boolean indexed = filterTerms.collect(filters(f -> f.conditionsItem(c -> c.headers(h -> h
            .nameLen("zilla:filter".length()).name(bytes("zilla:filter"), 0, "zilla:filter".length())
            .valuesItem(v -> v.value(m -> m.length(6).value(bytes("device"), 0, 6)))
            .valuesItem(v -> v.skip(s -> s.set(KafkaSkip.SKIP)))
            .valuesItem(v -> v.value(m -> m.length(1).value(bytes("7"), 0, 1)))))), terms);

        assertTrue(indexed);
        assertEquals(1, terms.size());
        assertEquals(KafkaFilterTerms.hashHeader(octets("zilla:filter"), octets("7")), terms.getInt(0));
    }

    @Test
    public void shouldCollectLastLiteralBeforeTrailingSkipMany()
    {
        final boolean indexed = filterTerms.collect(filters(f -> f.conditionsItem(c -> c.headers(h -> h
            .nameLen("zilla:filter".length()).name(bytes("zilla:filter"), 0, "zilla:filter".length())
            .valuesItem(v -> v.value(m -> m.length(1).value(bytes("a"), 0, 1)))
            .valuesItem(v -> v.value(m -> m.length(1).value(bytes("b"), 0, 1)))
            .valuesItem(v -> v.skip(s -> s.set(KafkaSkip.SKIP_MANY)))))), terms);

        assertTrue(indexed);
        assertEquals(KafkaFilterTerms.hashHeader(octets("zilla:filter"), octets("b")), terms.getInt(0));
    }

    @Test
    public void shouldNotCollectHeadersWithOnlySkips()
    {
        final boolean indexed = filterTerms.collect(filters(f -> f.conditionsItem(c -> c.headers(h -> h
            .nameLen("zilla:filter".length()).name(bytes("zilla:filter"), 0, "zilla:filter".length())
            .valuesItem(v -> v.skip(s -> s.set(KafkaSkip.SKIP_MANY)))))), terms);

        assertFalse(indexed);
    }

    @Test
    public void shouldCollectKeyTerm()
    {
        final boolean indexed = filterTerms.collect(
            filters(f -> f.conditionsItem(c -> c.key(k -> key(k, "match")))), terms);

        assertTrue(indexed);
        assertEquals(1, terms.size());
        assertEquals(KafkaFilterTerms.hashKey(octets("match")), terms.getInt(0));
    }

    @Test
    public void shouldNotCollectNegatedCondition()
    {
        final boolean indexed = filterTerms.collect(filters(f -> f.conditionsItem(c -> c.not(n -> n.condition(
            nc -> nc.header(h -> header(h, "zilla:qos", "0")))))), terms);

        assertFalse(indexed);
    }

    @Test
    public void shouldCollectFirstConditionThatYieldsTerm()
    {
        final boolean indexed = filterTerms.collect(filters(f -> f
            .conditionsItem(c -> c.not(n -> n.condition(nc -> nc.header(h -> header(h, "zilla:qos", "0")))))
            .conditionsItem(c -> c.header(h -> header(h, "first", "x")))
            .conditionsItem(c -> c.key(k -> key(k, "later")))), terms);

        assertTrue(indexed);
        assertEquals(1, terms.size());
        assertEquals(KafkaFilterTerms.hashHeader(octets("first"), octets("x")), terms.getInt(0));
    }

    @Test
    public void shouldCollectOneTermPerFilter()
    {
        final boolean indexed = filterTerms.collect(filters(
            f -> f.conditionsItem(c -> c.header(h -> header(h, "name", "one"))),
            f -> f.conditionsItem(c -> c.header(h -> header(h, "name", "two")))), terms);

        assertTrue(indexed);
        assertEquals(2, terms.size());
        assertEquals(KafkaFilterTerms.hashHeader(octets("name"), octets("one")), terms.getInt(0));
        assertEquals(KafkaFilterTerms.hashHeader(octets("name"), octets("two")), terms.getInt(1));
    }

    @Test
    public void shouldNotCollectWhenAnyFilterHasNoTerm()
    {
        final boolean indexed = filterTerms.collect(filters(
            f -> f.conditionsItem(c -> c.header(h -> header(h, "name", "one"))),
            f -> f.conditionsItem(c -> c.not(n -> n.condition(nc -> nc.header(h -> header(h, "name", "two")))))), terms);

        assertFalse(indexed);
    }

    @Test
    public void shouldNotCollectWhenNoFilters()
    {
        assertFalse(filterTerms.collect(filters(), terms));
        assertEquals(0, terms.size());
    }

    @Test
    public void shouldClearTermsBetweenCalls()
    {
        filterTerms.collect(filters(f -> f.conditionsItem(c -> c.header(h -> header(h, "name", "one")))), terms);
        filterTerms.collect(filters(f -> f.conditionsItem(c -> c.header(h -> header(h, "name", "two")))), terms);

        assertEquals(1, terms.size());
        assertEquals(KafkaFilterTerms.hashHeader(octets("name"), octets("two")), terms.getInt(0));
    }

    @Test
    public void shouldHashHeaderNameAndValue()
    {
        assertEquals(
            KafkaFilterTerms.hashHeader(octets("zilla:filter"), octets("1")),
            KafkaFilterTerms.hashHeader(octets("zilla:filter"), octets("1")));
        assertNotEquals(
            KafkaFilterTerms.hashHeader(octets("zilla:filter"), octets("1")),
            KafkaFilterTerms.hashHeader(octets("zilla:qos"), octets("1")));
        assertNotEquals(
            KafkaFilterTerms.hashHeader(octets("zilla:filter"), octets("1")),
            KafkaFilterTerms.hashHeader(octets("zilla:filter"), octets("2")));
    }

    @Test
    public void shouldHashHeaderWithNullValue()
    {
        assertEquals(
            KafkaFilterTerms.hashHeader(octets("name"), null),
            KafkaFilterTerms.hashHeader(octets("name"), null));
        assertNotEquals(
            KafkaFilterTerms.hashHeader(octets("name"), null),
            KafkaFilterTerms.hashHeader(octets("name"), octets("")));
    }

    @Test
    public void shouldHashKeyDistinctFromHeader()
    {
        assertEquals(KafkaFilterTerms.hashKey(octets("a")), KafkaFilterTerms.hashKey(octets("a")));
        assertNotEquals(KafkaFilterTerms.hashKey(octets("a")), KafkaFilterTerms.hashKey(octets("b")));
        assertNotEquals(KafkaFilterTerms.hashKey(octets("a")), KafkaFilterTerms.hashHeader(octets("a"), octets("a")));
    }

    @SafeVarargs
    private Array32FW<KafkaFilterFW> filters(
        Consumer<KafkaFilterFW.Builder>... filters)
    {
        final Array32FW.Builder<KafkaFilterFW.Builder, KafkaFilterFW> builder =
            new Array32FW.Builder<>(new KafkaFilterFW.Builder(), new KafkaFilterFW())
                .wrap(filtersBuffer, 0, filtersBuffer.capacity());

        for (Consumer<KafkaFilterFW.Builder> filter : filters)
        {
            builder.item(filter);
        }

        return builder.build();
    }

    private static KafkaKeyFW.Builder key(
        KafkaKeyFW.Builder builder,
        String value)
    {
        return builder.length(value.length()).value(bytes(value), 0, value.length());
    }

    private static KafkaHeaderFW.Builder header(
        KafkaHeaderFW.Builder builder,
        String name,
        String value)
    {
        return builder.nameLen(name.length()).name(bytes(name), 0, name.length())
            .valueLen(value.length()).value(bytes(value), 0, value.length());
    }

    private static OctetsFW octets(
        String value)
    {
        final DirectBufferEx buffer = bytes(value);
        return new OctetsFW().wrap(buffer, 0, buffer.capacity());
    }

    private static DirectBufferEx bytes(
        String value)
    {
        return new UnsafeBufferEx(value.getBytes(UTF_8));
    }
}
