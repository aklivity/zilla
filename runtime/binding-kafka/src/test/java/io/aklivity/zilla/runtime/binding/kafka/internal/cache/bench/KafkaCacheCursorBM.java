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
package io.aklivity.zilla.runtime.binding.kafka.internal.cache.bench;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Arrays;
import java.util.Random;

import org.agrona.IoUtil;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.IntArrayList;
import org.agrona.collections.MutableInteger;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration;
import io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCacheCursorFactory;
import io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCacheCursorFactory.KafkaCacheCursor;
import io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCacheCursorFactory.KafkaFilterCondition;
import io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCacheKeyEnvelope;
import io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCacheModel;
import io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCachePartition;
import io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCachePartition.Node;
import io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCacheTopicConfig;
import io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCacheTrailerEnvelope;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaDeltaType;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaEvaluation;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaFilterFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaHeaderFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaKeyFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaSkip;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaTimestampType;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.cache.KafkaCacheEntryFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;

/**
 * Measures delivering one appended message to every subscriber cursor positioned at the live tail of a
 * partition, where each subscriber filters on one MQTT topic: a {@code zilla:filter} headers condition
 * with one value per topic level, followed by a negated {@code zilla:qos} header condition. One operation
 * appends one message, then drains every cursor, so the score is the per-message cost of the scan across
 * all subscribers. With {@code leaf=leading} each filter is preceded by a plain header condition on the
 * last topic level. With {@code topology=broadcast} every subscriber has the same filter and every message
 * is published to that one topic, so each message is delivered to every subscriber. With
 * {@code topology=wildcard} messages go to {@code device/<site>/<sensor>} topics and the subscriptions are a
 * mix of exact topics and overlapping {@code +} and {@code #} wildcards, so each message matches many
 * subscribers and wakes more that do not match. With
 * {@code filters=none} the cursors have no filter conditions, which isolates the cursor cost from the
 * filter evaluation.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(MICROSECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 1, timeUnit = SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = SECONDS)
public class KafkaCacheCursorBM
{
    private static final int WARM_MESSAGES = 5;
    private static final int FILTER_LEVELS = 3;
    private static final int VALUE_SIZE = 256;
    private static final String BROADCAST_LAST_LEVEL = "all";
    private static final int SITES = 10;
    private static final String[] SENSORS = {"temp", "humidity", "pressure", "vibration", "flow"};
    private static final int SEGMENT_BYTES = 64 * 1024 * 1024;
    private static final int SEGMENT_INDEX_BYTES = 1024 * 1024;

    private static final byte[] FILTER_HEADER_NAME = "zilla:filter".getBytes(UTF_8);
    private static final byte[] LOCAL_HEADER_NAME = "zilla:local".getBytes(UTF_8);
    private static final byte[] QOS_HEADER_NAME = "zilla:qos".getBytes(UTF_8);

    @Param({"200", "800"})
    public int subscribers;

    @Param({"200"})
    public int active;

    @Param({"none", "leading"})
    public String leaf;

    @Param({"scan", "inverted"})
    public String dispatch;

    @Param({"distinct", "broadcast", "wildcard"})
    public String topology;

    @Param({"topic", "none"})
    public String filters;

    private final KafkaCacheCursorFactory cursorFactory = new KafkaCacheCursorFactory(65536);
    private final KafkaCacheEntryFW entryRO = new KafkaCacheEntryFW();
    private final KafkaCacheKeyEnvelope keyEnvelope = new KafkaCacheKeyEnvelope();
    private final KafkaCacheTrailerEnvelope valueEnvelope = new KafkaCacheTrailerEnvelope();
    private final MutableInteger entryMark = new MutableInteger();
    private final MutableInteger valueMark = new MutableInteger();
    private KafkaKeyFW[] keys;
    private Array32FW<?>[] headers;
    private int[][] termHashes;
    private final Int2ObjectHashMap<IntArrayList> subscribersByTerm = new Int2ObjectHashMap<>();
    private final IntArrayList termlessSubscribers = new IntArrayList();
    private String[][] topics;
    private String[][] patterns;

    private OctetsFW value;
    private Path location;
    private KafkaCachePartition partition;
    private Node head;
    private KafkaCacheCursor[] cursors;
    private long nextOffset;
    private int appendedIndex;

    @Setup(Level.Trial)
    public void validate() throws IOException
    {
        keys = new KafkaKeyFW[active];
        headers = new Array32FW<?>[active];
        termHashes = new int[active][];
        topics = new String[active][];

        buildMessages();
        init();

        final int[] hitsByCursor = new int[subscribers];
        for (int message = 0; message < 3; message++)
        {
            Arrays.fill(hitsByCursor, 0);
            final int[] expected = expectedHits((int) (nextOffset % active));
            appendMessage();
            drain(hitsByCursor);

            if (!Arrays.equals(hitsByCursor, expected))
            {
                throw new IllegalStateException(String.format(
                    "invalid benchmark: message %d hits %s, expected %s",
                    message, Arrays.toString(hitsByCursor), Arrays.toString(expected)));
            }
        }

        dispose();
    }

    @Setup(Level.Iteration)
    public void init() throws IOException
    {
        location = Files.createTempDirectory("kafka-cache-cursor-bm");
        final KafkaCacheTopicConfig config = new KafkaCacheTopicConfig(new KafkaConfiguration());
        config.segmentBytes = SEGMENT_BYTES;
        config.segmentIndexBytes = SEGMENT_INDEX_BYTES;
        partition = new KafkaCachePartition(Clock.systemUTC(), location, config, "cache", "test", 0, 65536, long[]::new);
        head = partition.append(0L);
        nextOffset = 0L;

        patterns = newPatterns();
        cursors = new KafkaCacheCursor[subscribers];
        for (int i = 0; i < subscribers; i++)
        {
            final KafkaFilterCondition condition = cursorFactory.asCondition(newFilters(i), KafkaEvaluation.EAGER);
            cursors[i] = cursorFactory.newCursor(condition, KafkaDeltaType.NONE);
            cursors[i].init(head, 0L, -1L);
        }

        subscribersByTerm.clear();
        termlessSubscribers.clear();
        for (int i = 0; i < subscribers; i++)
        {
            final String selective = termOf(i);
            if (selective == null)
            {
                termlessSubscribers.addInt(i);
            }
            else
            {
                subscribersByTerm.computeIfAbsent(termHash(selective.getBytes(UTF_8)), h -> new IntArrayList()).addInt(i);
            }
        }

        for (int i = 0; i < WARM_MESSAGES; i++)
        {
            appendMessage();
            drain(null);
        }
    }

    @TearDown(Level.Iteration)
    public void dispose()
    {
        for (KafkaCacheCursor cursor : cursors)
        {
            cursor.close();
        }
        partition.deleteAll();
        IoUtil.delete(location.toFile(), true);
    }

    @Benchmark
    public int deliver()
    {
        appendMessage();
        return drain(null);
    }

    private void appendMessage()
    {
        final int index = (int) (nextOffset % active);
        appendedIndex = index;
        final KafkaKeyFW key = keys[index];
        @SuppressWarnings("unchecked")
        final Array32FW<KafkaHeaderFW> messageHeaders = (Array32FW<KafkaHeaderFW>) headers[index];

        head = partition.newHeadIfNecessary(nextOffset, key, value.sizeof(), 0, messageHeaders.sizeof());
        partition.writeEntry(null, 1L, 1L, 0L, nextOffset, entryMark, valueMark, 0L, KafkaTimestampType.ADVISORY, -1L,
            key, messageHeaders, value, 0x00, KafkaDeltaType.NONE, KafkaCacheModel.NONE, KafkaCacheModel.NONE,
            keyEnvelope, valueEnvelope, 256, false);
        nextOffset++;
    }

    private int drain(
        int[] hitsByCursor)
    {
        int hits = 0;
        if ("inverted".equals(dispatch))
        {
            final int[] hashes = termHashes[appendedIndex];
            for (int h = 0; h < FILTER_LEVELS; h++)
            {
                final IntArrayList candidates = subscribersByTerm.get(hashes[h]);
                for (int c = 0; candidates != null && c < candidates.size(); c++)
                {
                    hits += drainCursor(candidates.getInt(c), hitsByCursor);
                }
            }
            for (int c = 0; c < termlessSubscribers.size(); c++)
            {
                hits += drainCursor(termlessSubscribers.getInt(c), hitsByCursor);
            }
        }
        else
        {
            for (int i = 0; i < cursors.length; i++)
            {
                hits += drainCursor(i, hitsByCursor);
            }
        }
        return hits;
    }

    private int drainCursor(
        int index,
        int[] hitsByCursor)
    {
        int hits = 0;
        final KafkaCacheCursor cursor = cursors[index];
        KafkaCacheEntryFW entry;
        while ((entry = cursor.next(entryRO)) != null)
        {
            hits++;
            if (hitsByCursor != null)
            {
                hitsByCursor[index]++;
            }
            cursor.advance(entry.offset$() + 1);
        }
        return hits;
    }

    private boolean broadcast()
    {
        return "broadcast".equals(topology);
    }

    private boolean wildcard()
    {
        return "wildcard".equals(topology);
    }

    private String[] messageLevels(
        int index)
    {
        return broadcast()
            ? new String[] {"device", "broadcast", BROADCAST_LAST_LEVEL}
            : wildcard()
                ? new String[] {"device", Integer.toString(1 + index % SITES), SENSORS[(index / SITES) % SENSORS.length]}
                : new String[] {"device", "1", Integer.toString(index)};
    }

    private String[][] newPatterns()
    {
        final String[][] result = new String[subscribers][];
        final Random random = new Random(7L);
        for (int i = 0; i < subscribers; i++)
        {
            if (wildcard())
            {
                final int kind = random.nextInt(100);
                final String site = Integer.toString(1 + random.nextInt(SITES));
                final String sensor = SENSORS[random.nextInt(SENSORS.length)];
                result[i] = kind < 25 ? new String[] {"device", site, sensor}
                    : kind < 50 ? new String[] {"device", site, "+"}
                    : kind < 75 ? new String[] {"device", "+", sensor}
                    : kind < 90 ? new String[] {"device", site, "#"}
                    : kind < 97 ? new String[] {"device", "#"}
                    : new String[] {"#"};
            }
            else if (broadcast())
            {
                result[i] = new String[] {"device", "broadcast", BROADCAST_LAST_LEVEL};
            }
            else
            {
                result[i] = i < active
                    ? new String[] {"device", "1", Integer.toString(i)}
                    : new String[] {"device", "idle" + (i - active), Integer.toString(i)};
            }
        }
        return result;
    }

    private String termOf(
        int subscriber)
    {
        final String[] pattern = patterns[subscriber];
        String term = null;
        for (String level : pattern)
        {
            if (!"+".equals(level) && !"#".equals(level))
            {
                term = level;
            }
        }
        return term;
    }

    private int[] expectedHits(
        int index)
    {
        final int[] expected = new int[subscribers];
        for (int i = 0; i < subscribers; i++)
        {
            expected[i] = "none".equals(filters) || matches(patterns[i], topics[index]) ? 1 : 0;
        }
        return expected;
    }

    private static boolean matches(
        String[] pattern,
        String[] topic)
    {
        boolean matched = true;
        boolean many = false;
        for (int i = 0; matched && !many && i < pattern.length; i++)
        {
            if ("#".equals(pattern[i]))
            {
                many = true;
                matched = i < topic.length;
            }
            else
            {
                matched = i < topic.length && ("+".equals(pattern[i]) || pattern[i].equals(topic[i]));
            }
        }
        return matched && (many || pattern.length == topic.length);
    }

    private static int termHash(
        byte[] term)
    {
        return Arrays.hashCode(term);
    }

    private Array32FW<KafkaFilterFW> newFilters(
        int subscriber)
    {
        final String[] levels = patterns[subscriber];
        final boolean leading = "leading".equals(leaf);
        final MutableDirectBufferEx buffer = new UnsafeBufferEx(ByteBuffer.allocate(2048));

        final Array32FW.Builder<KafkaFilterFW.Builder, KafkaFilterFW> builder =
            new Array32FW.Builder<>(new KafkaFilterFW.Builder(), new KafkaFilterFW())
                .wrap(buffer, 0, buffer.capacity());

        if (!"none".equals(filters))
        {
            builder.item(f -> buildFilter(f, levels, leading));
        }

        return builder.build();
    }

    private static void buildFilter(
        KafkaFilterFW.Builder filter,
        String[] levels,
        boolean leading)
    {
        if (leading)
        {
            final byte[] lastLevel = levels[levels.length - 1].getBytes(UTF_8);
            filter.conditionsItem(c -> c.header(h -> buildHeader(h, FILTER_HEADER_NAME, lastLevel)));
        }

        filter.conditionsItem(c -> c.headers(hs ->
        {
            hs.nameLen(FILTER_HEADER_NAME.length).name(n -> n.set(FILTER_HEADER_NAME));
            for (String level : levels)
            {
                final byte[] bytes = level.getBytes(UTF_8);
                if ("+".equals(level))
                {
                    hs.valuesItem(v -> v.skip(k -> k.set(KafkaSkip.SKIP)));
                }
                else if ("#".equals(level))
                {
                    hs.valuesItem(v -> v.skip(k -> k.set(KafkaSkip.SKIP_MANY)));
                }
                else
                {
                    hs.valuesItem(v -> v.value(m -> m.length(bytes.length).value(o -> o.set(bytes))));
                }
            }
        }));

        final byte[] qosZero = new byte[] {'0'};
        filter.conditionsItem(c -> c.not(n -> n.condition(nc -> nc.header(h -> buildHeader(h, QOS_HEADER_NAME, qosZero)))));
    }

    private static void buildHeader(
        KafkaHeaderFW.Builder header,
        byte[] name,
        byte[] headerValue)
    {
        header.nameLen(name.length).name(n -> n.set(name))
            .valueLen(headerValue.length).value(v -> v.set(headerValue));
    }

    private void buildMessages()
    {
        final byte[] valueBytes = new byte[VALUE_SIZE];
        Arrays.fill(valueBytes, (byte) 'x');
        value = new OctetsFW.Builder()
            .wrap(new UnsafeBufferEx(ByteBuffer.allocate(VALUE_SIZE + 16)), 0, VALUE_SIZE + 16)
            .set(valueBytes)
            .build();

        for (int i = 0; i < active; i++)
        {
            final String[] levels = messageLevels(i);
            topics[i] = levels;
            final byte[] topic = String.join("/", levels).getBytes(UTF_8);
            final byte[] client = ("client" + i).getBytes(UTF_8);
            final MutableDirectBufferEx buffer = new UnsafeBufferEx(ByteBuffer.allocate(1024));

            final KafkaKeyFW key = new KafkaKeyFW.Builder()
                .wrap(buffer, 0, buffer.capacity())
                .length(topic.length)
                .value(k -> k.set(topic))
                .build();

            termHashes[i] = new int[] {termHash(levels[0].getBytes(UTF_8)), termHash(levels[1].getBytes(UTF_8)),
                termHash(levels[2].getBytes(UTF_8)), termHash(client), termHash("1".getBytes(UTF_8))};
            keys[i] = key;
            headers[i] = new Array32FW.Builder<>(new KafkaHeaderFW.Builder(), new KafkaHeaderFW())
                .wrap(buffer, key.limit(), buffer.capacity())
                .item(h -> buildHeader(h, FILTER_HEADER_NAME, levels[0].getBytes(UTF_8)))
                .item(h -> buildHeader(h, FILTER_HEADER_NAME, levels[1].getBytes(UTF_8)))
                .item(h -> buildHeader(h, FILTER_HEADER_NAME, levels[2].getBytes(UTF_8)))
                .item(h -> buildHeader(h, LOCAL_HEADER_NAME, client))
                .item(h -> buildHeader(h, QOS_HEADER_NAME, "1".getBytes(UTF_8)))
                .build();
        }
    }
}
