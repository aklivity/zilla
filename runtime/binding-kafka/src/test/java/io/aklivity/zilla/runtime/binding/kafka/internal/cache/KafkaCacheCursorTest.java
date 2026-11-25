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
package io.aklivity.zilla.runtime.binding.kafka.internal.cache;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.agrona.collections.MutableInteger;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration;
import io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCacheCursorFactory.KafkaCacheCursor;
import io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCacheCursorFactory.KafkaFilterCondition;
import io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCachePartition.Node;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaConditionFW;
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

public class KafkaCacheCursorTest
{
    private static final byte[] FILTER_HEADER_NAME = "zilla:filter".getBytes(UTF_8);
    private static final byte[] LOCAL_HEADER_NAME = "zilla:local".getBytes(UTF_8);
    private static final byte[] QOS_HEADER_NAME = "zilla:qos".getBytes(UTF_8);
    private static final String[] LEVELS = {"a", "b", "c", "d"};
    private static final String[] CLIENTS = {"c0", "c1", "c2"};
    private static final int LEVEL_COUNT = 3;
    private static final int MESSAGES = 240;
    private static final int VALUE_SIZE = 128;
    private static final int SEGMENT_BYTES = 8 * 1024;
    private static final int SEGMENT_INDEX_BYTES = 64 * 1024;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private final KafkaCacheCursorFactory cursorFactory = new KafkaCacheCursorFactory(65536);
    private final KafkaCacheEntryFW entryRO = new KafkaCacheEntryFW();
    private final KafkaCacheKeyEnvelope keyEnvelope = new KafkaCacheKeyEnvelope();
    private final KafkaCacheTrailerEnvelope valueEnvelope = new KafkaCacheTrailerEnvelope();
    private final MutableInteger entryMark = new MutableInteger();
    private final MutableInteger valueMark = new MutableInteger();

    @Test
    public void shouldDeliverExactlyTheEntriesAcceptedByRandomFilters() throws Exception
    {
        final Random random = new Random(0x4a7d);

        for (int trial = 0; trial < 8; trial++)
        {
            final Path location = tempFolder.newFolder().toPath();
            final KafkaCacheTopicConfig config = new KafkaCacheTopicConfig(new KafkaConfiguration());
            config.segmentBytes = SEGMENT_BYTES;
            config.segmentIndexBytes = SEGMENT_INDEX_BYTES;

            final KafkaCachePartition partition =
                new KafkaCachePartition(Clock.systemUTC(), location, config, "cache", "test", 0, 65536, long[]::new);
            final Node first = partition.append(0L);

            final List<Message> messages = new ArrayList<>();
            Node head = first;
            int segments = 1;
            for (int offset = 0; offset < MESSAGES; offset++)
            {
                final Message message = newMessage(random);
                messages.add(message);

                final Node next = partition.newHeadIfNecessary(offset, message.key, VALUE_SIZE, 0, message.headers.sizeof());
                segments += next != head ? 1 : 0;
                head = next;
                appendEntry(partition, offset, message);
            }

            assertEquals("segments rolled", true, segments > 4);

            for (int filter = 0; filter < 40; filter++)
            {
                final List<List<Condition>> alternatives = newAlternatives(random);
                final int[] starts = {0, random.nextInt(MESSAGES), MESSAGES - 1, MESSAGES, MESSAGES + 3};
                final int start = starts[random.nextInt(starts.length)];

                final List<Integer> expected = new ArrayList<>();
                for (int offset = start; offset < MESSAGES; offset++)
                {
                    if (accepts(alternatives, messages.get(offset)))
                    {
                        expected.add(offset);
                    }
                }

                final KafkaFilterCondition condition = cursorFactory.asCondition(newFilters(alternatives), KafkaEvaluation.EAGER);
                final KafkaCacheCursor cursor = cursorFactory.newCursor(condition, KafkaDeltaType.NONE);
                cursor.init(first, start, -1L);

                final List<Integer> actual = new ArrayList<>();
                KafkaCacheEntryFW entry;
                while ((entry = cursor.next(entryRO)) != null)
                {
                    actual.add((int) entry.offset$());
                    cursor.advance(entry.offset$() + 1);
                }
                cursor.close();

                assertEquals(String.format("trial %d filter %d start %d %s", trial, filter, start, alternatives),
                    expected, actual);
            }

            partition.deleteAll();
        }
    }

    private void appendEntry(
        KafkaCachePartition partition,
        int offset,
        Message message)
    {
        final byte[] valueBytes = new byte[VALUE_SIZE];
        final OctetsFW value = new OctetsFW.Builder()
            .wrap(new UnsafeBufferEx(ByteBuffer.allocate(VALUE_SIZE + 16)), 0, VALUE_SIZE + 16)
            .set(valueBytes)
            .build();

        partition.writeEntry(null, 1L, 1L, 0L, offset, entryMark, valueMark, 0L, KafkaTimestampType.ADVISORY, -1L,
            message.key, message.headers, value, 0x00, KafkaDeltaType.NONE, KafkaCacheModel.NONE, KafkaCacheModel.NONE,
            keyEnvelope, valueEnvelope, 256, false);
    }

    private static Message newMessage(
        Random random)
    {
        final String[] levels = new String[LEVEL_COUNT];
        for (int level = 0; level < LEVEL_COUNT; level++)
        {
            levels[level] = LEVELS[random.nextInt(LEVELS.length)];
        }

        final String client = CLIENTS[random.nextInt(CLIENTS.length)];
        final int qos = random.nextInt(3);
        final byte[] topic = String.join("/", levels).getBytes(UTF_8);
        final MutableDirectBufferEx buffer = new UnsafeBufferEx(ByteBuffer.allocate(1024));

        final KafkaKeyFW key = new KafkaKeyFW.Builder()
            .wrap(buffer, 0, buffer.capacity())
            .length(topic.length)
            .value(k -> k.set(topic))
            .build();

        final Array32FW<KafkaHeaderFW> headers = new Array32FW.Builder<>(new KafkaHeaderFW.Builder(), new KafkaHeaderFW())
            .wrap(buffer, key.limit(), buffer.capacity())
            .item(h -> buildHeader(h, FILTER_HEADER_NAME, levels[0].getBytes(UTF_8)))
            .item(h -> buildHeader(h, FILTER_HEADER_NAME, levels[1].getBytes(UTF_8)))
            .item(h -> buildHeader(h, FILTER_HEADER_NAME, levels[2].getBytes(UTF_8)))
            .item(h -> buildHeader(h, LOCAL_HEADER_NAME, client.getBytes(UTF_8)))
            .item(h -> buildHeader(h, QOS_HEADER_NAME, Integer.toString(qos).getBytes(UTF_8)))
            .build();

        return new Message(levels, client, qos, key, headers);
    }

    private static List<List<Condition>> newAlternatives(
        Random random)
    {
        final List<List<Condition>> alternatives = new ArrayList<>();
        final int count = 1 + random.nextInt(3);
        for (int alternative = 0; alternative < count; alternative++)
        {
            final List<Condition> conditions = new ArrayList<>();
            final int size = 1 + random.nextInt(3);
            for (int condition = 0; condition < size; condition++)
            {
                conditions.add(newCondition(random, true));
            }
            alternatives.add(conditions);
        }
        return alternatives;
    }

    private static Condition newCondition(
        Random random,
        boolean negatable)
    {
        final int kind = random.nextInt(negatable ? 5 : 4);
        final Condition condition;

        switch (kind)
        {
        case 0:
            condition = Condition.key(topicOf(random));
            break;
        case 1:
            condition = Condition.header(LOCAL_HEADER_NAME, CLIENTS[random.nextInt(CLIENTS.length)]);
            break;
        case 2:
            condition = Condition.header(QOS_HEADER_NAME, Integer.toString(random.nextInt(3)));
            break;
        case 3:
            condition = Condition.pattern(newPattern(random));
            break;
        default:
            condition = Condition.not(newCondition(random, false));
            break;
        }

        return condition;
    }

    private static String topicOf(
        Random random)
    {
        final String[] levels = new String[LEVEL_COUNT];
        for (int level = 0; level < LEVEL_COUNT; level++)
        {
            levels[level] = LEVELS[random.nextInt(LEVELS.length)];
        }
        return String.join("/", levels);
    }

    private static String[] newPattern(
        Random random)
    {
        final boolean many = random.nextInt(4) == 0;
        final int length = (many ? 2 : 1) + random.nextInt(many ? LEVEL_COUNT - 1 : LEVEL_COUNT);
        final String[] pattern = new String[length];
        for (int level = 0; level < length; level++)
        {
            pattern[level] = random.nextInt(4) == 0 ? "+" : LEVELS[random.nextInt(LEVELS.length)];
        }
        if (many)
        {
            pattern[length - 1] = "#";
        }
        pattern[random.nextInt(many ? length - 1 : length)] = LEVELS[random.nextInt(LEVELS.length)];
        return pattern;
    }

    private static boolean accepts(
        List<List<Condition>> alternatives,
        Message message)
    {
        boolean accepted = false;
        for (int alternative = 0; !accepted && alternative < alternatives.size(); alternative++)
        {
            boolean all = true;
            for (Condition condition : alternatives.get(alternative))
            {
                all &= condition.test(message);
            }
            accepted = all;
        }
        return accepted;
    }

    private static Array32FW<KafkaFilterFW> newFilters(
        List<List<Condition>> alternatives)
    {
        final MutableDirectBufferEx buffer = new UnsafeBufferEx(ByteBuffer.allocate(4096));

        final Array32FW.Builder<KafkaFilterFW.Builder, KafkaFilterFW> builder =
            new Array32FW.Builder<>(new KafkaFilterFW.Builder(), new KafkaFilterFW())
                .wrap(buffer, 0, buffer.capacity());

        for (List<Condition> conditions : alternatives)
        {
            builder.item(f ->
            {
                for (Condition condition : conditions)
                {
                    f.conditionsItem(condition::write);
                }
            });
        }

        return builder.build();
    }

    private static void buildHeader(
        KafkaHeaderFW.Builder header,
        byte[] name,
        byte[] headerValue)
    {
        header.nameLen(name.length).name(n -> n.set(name))
            .valueLen(headerValue.length).value(v -> v.set(headerValue));
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

    private static final class Message
    {
        private final String[] levels;
        private final String client;
        private final int qos;
        private final KafkaKeyFW key;
        private final Array32FW<KafkaHeaderFW> headers;

        private Message(
            String[] levels,
            String client,
            int qos,
            KafkaKeyFW key,
            Array32FW<KafkaHeaderFW> headers)
        {
            this.levels = levels;
            this.client = client;
            this.qos = qos;
            this.key = key;
            this.headers = headers;
        }
    }

    private abstract static class Condition
    {
        abstract void write(
            KafkaConditionFW.Builder condition);

        abstract boolean test(
            Message message);

        static Condition key(
            String topic)
        {
            final byte[] bytes = topic.getBytes(UTF_8);
            return new Condition()
            {
                @Override
                void write(
                    KafkaConditionFW.Builder condition)
                {
                    condition.key(k -> k.length(bytes.length).value(v -> v.set(bytes)));
                }

                @Override
                boolean test(
                    Message message)
                {
                    return String.join("/", message.levels).equals(topic);
                }

                @Override
                public String toString()
                {
                    return "key(" + topic + ")";
                }
            };
        }

        static Condition header(
            byte[] name,
            String value)
        {
            final byte[] bytes = value.getBytes(UTF_8);
            return new Condition()
            {
                @Override
                void write(
                    KafkaConditionFW.Builder condition)
                {
                    condition.header(h -> buildHeader(h, name, bytes));
                }

                @Override
                boolean test(
                    Message message)
                {
                    return name == LOCAL_HEADER_NAME
                        ? message.client.equals(value)
                        : Integer.toString(message.qos).equals(value);
                }

                @Override
                public String toString()
                {
                    return new String(name, UTF_8) + "=" + value;
                }
            };
        }

        static Condition pattern(
            String[] pattern)
        {
            return new Condition()
            {
                @Override
                void write(
                    KafkaConditionFW.Builder condition)
                {
                    condition.headers(hs ->
                    {
                        hs.nameLen(FILTER_HEADER_NAME.length).name(n -> n.set(FILTER_HEADER_NAME));
                        for (String level : pattern)
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
                    });
                }

                @Override
                boolean test(
                    Message message)
                {
                    return matches(pattern, message.levels);
                }

                @Override
                public String toString()
                {
                    return "filter" + String.join("/", pattern);
                }
            };
        }

        static Condition not(
            Condition nested)
        {
            return new Condition()
            {
                @Override
                void write(
                    KafkaConditionFW.Builder condition)
                {
                    condition.not(n -> n.condition(nested::write));
                }

                @Override
                boolean test(
                    Message message)
                {
                    return !nested.test(message);
                }

                @Override
                public String toString()
                {
                    return "not(" + nested + ")";
                }
            };
        }
    }
}
