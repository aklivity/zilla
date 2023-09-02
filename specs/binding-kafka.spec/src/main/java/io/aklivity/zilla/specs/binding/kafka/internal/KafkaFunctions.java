/*
 * Copyright 2021-2023 Aklivity Inc.
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
package io.aklivity.zilla.specs.binding.kafka.internal;

import static io.aklivity.zilla.specs.binding.kafka.internal.types.KafkaOffsetFW.Builder.DEFAULT_LATEST_OFFSET;
import static java.lang.Long.highestOneBit;
import static java.lang.Long.numberOfTrailingZeros;
import static java.lang.System.currentTimeMillis;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.nio.ByteBuffer;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.kaazing.k3po.lang.el.BytesMatcher;
import org.kaazing.k3po.lang.el.Function;
import org.kaazing.k3po.lang.el.spi.FunctionMapperSpi;

import io.aklivity.zilla.specs.binding.kafka.internal.types.Array32FW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.KafkaAckMode;
import io.aklivity.zilla.specs.binding.kafka.internal.types.KafkaCapabilities;
import io.aklivity.zilla.specs.binding.kafka.internal.types.KafkaConditionFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.KafkaDeltaFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.KafkaDeltaType;
import io.aklivity.zilla.specs.binding.kafka.internal.types.KafkaDeltaTypeFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.KafkaEvaluation;
import io.aklivity.zilla.specs.binding.kafka.internal.types.KafkaEvaluationFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.KafkaFilterFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.KafkaHeaderFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.KafkaHeadersFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.KafkaIsolation;
import io.aklivity.zilla.specs.binding.kafka.internal.types.KafkaIsolationFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.KafkaKeyFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.KafkaNotFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.KafkaOffsetFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.KafkaOffsetType;
import io.aklivity.zilla.specs.binding.kafka.internal.types.KafkaSkip;
import io.aklivity.zilla.specs.binding.kafka.internal.types.KafkaSkipFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.KafkaTransactionFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.KafkaTransactionResult;
import io.aklivity.zilla.specs.binding.kafka.internal.types.KafkaValueFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.KafkaValueMatchFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.OctetsFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.String16FW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.String8FW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.stream.KafkaApi;
import io.aklivity.zilla.specs.binding.kafka.internal.types.stream.KafkaBeginExFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.stream.KafkaBootstrapBeginExFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.stream.KafkaConsumerBeginExFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.stream.KafkaConsumerDataExFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.stream.KafkaDataExFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.stream.KafkaDescribeBeginExFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.stream.KafkaDescribeDataExFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.stream.KafkaFetchBeginExFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.stream.KafkaFetchDataExFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.stream.KafkaFetchFlushExFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.stream.KafkaFlushExFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.stream.KafkaGroupBeginExFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.stream.KafkaGroupFlushExFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.stream.KafkaGroupMemberFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.stream.KafkaGroupMemberMetadataFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.stream.KafkaMergedBeginExFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.stream.KafkaMergedConsumerFlushExFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.stream.KafkaMergedDataExFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.stream.KafkaMergedFetchFlushExFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.stream.KafkaMergedFlushExFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.stream.KafkaMetaBeginExFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.stream.KafkaMetaDataExFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.stream.KafkaOffsetCommitBeginExFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.stream.KafkaOffsetCommitDataExFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.stream.KafkaOffsetFetchBeginExFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.stream.KafkaOffsetFetchDataExFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.stream.KafkaProduceBeginExFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.stream.KafkaProduceDataExFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.stream.KafkaProduceFlushExFW;
import io.aklivity.zilla.specs.binding.kafka.internal.types.stream.KafkaResetExFW;

public final class KafkaFunctions
{
    @Function
    public static KafkaBeginExBuilder beginEx()
    {
        return new KafkaBeginExBuilder();
    }

    @Function
    public static KafkaBeginExMatcherBuilder matchBeginEx()
    {
        return new KafkaBeginExMatcherBuilder();
    }

    @Function
    public static KafkaDataExBuilder dataEx()
    {
        return new KafkaDataExBuilder();
    }

    @Function
    public static KafkaDataExMatcherBuilder matchDataEx()
    {
        return new KafkaDataExMatcherBuilder();
    }

    @Function
    public static KafkaFlushExBuilder flushEx()
    {
        return new KafkaFlushExBuilder();
    }

    @Function
    public static KafkaFlushExMatcherBuilder matchFlushEx()
    {
        return new KafkaFlushExMatcherBuilder();
    }

    @Function
    public static KafkaResetExBuilder resetEx()
    {
        return new KafkaResetExBuilder();
    }

    @Function
    public static int length(
        String value)
    {
        return value.length();
    }

    @Function
    public static short lengthAsShort(
        String value)
    {
        return (short) value.length();
    }

    @Function
    public static int newRequestId()
    {
        return ThreadLocalRandom.current().nextInt();
    }

    @Function
    public static byte[] randomBytes(
        int length)
    {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++)
        {
            bytes[i] = (byte) ThreadLocalRandom.current().nextInt(0x100);
        }
        return bytes;
    }

    @Function
    public static long timestamp()
    {
        return currentTimeMillis();
    }

    @Function
    public static byte[] varint(
        long value)
    {
        final long bits = (value << 1) ^ (value >> 63);

        switch (bits != 0L ? (int) Math.ceil((1 + numberOfTrailingZeros(highestOneBit(bits))) / 7.0) : 1)
        {
        case 1:
            return new byte[]
            {
                (byte) ((bits >> 0) & 0x7f)
            };
        case 2:
            return new byte[]
            {
                (byte) ((bits >> 0) & 0x7f | 0x80),
                (byte) ((bits >> 7) & 0x7f)
            };
        case 3:
            return new byte[]
            {
                (byte) ((bits >> 0) & 0x7f | 0x80),
                (byte) ((bits >> 7) & 0x7f | 0x80),
                (byte) ((bits >> 14) & 0x7f)
            };
        case 4:
            return new byte[]
            {
                (byte) ((bits >> 0) & 0x7f | 0x80),
                (byte) ((bits >> 7) & 0x7f | 0x80),
                (byte) ((bits >> 14) & 0x7f | 0x80),
                (byte) ((bits >> 21) & 0x7f)
            };
        case 5:
            return new byte[]
            {
                (byte) ((bits >> 0) & 0x7f | 0x80),
                (byte) ((bits >> 7) & 0x7f | 0x80),
                (byte) ((bits >> 14) & 0x7f | 0x80),
                (byte) ((bits >> 21) & 0x7f | 0x80),
                (byte) ((bits >> 28) & 0x7f)
            };
        case 6:
            return new byte[]
            {
                (byte) ((bits >> 0) & 0x7f | 0x80),
                (byte) ((bits >> 7) & 0x7f | 0x80),
                (byte) ((bits >> 14) & 0x7f | 0x80),
                (byte) ((bits >> 21) & 0x7f | 0x80),
                (byte) ((bits >> 28) & 0x7f | 0x80),
                (byte) ((bits >> 35) & 0x7f)
            };
        case 7:
            return new byte[]
            {
                (byte) ((bits >> 0) & 0x7f | 0x80),
                (byte) ((bits >> 7) & 0x7f | 0x80),
                (byte) ((bits >> 14) & 0x7f | 0x80),
                (byte) ((bits >> 21) & 0x7f | 0x80),
                (byte) ((bits >> 28) & 0x7f | 0x80),
                (byte) ((bits >> 35) & 0x7f | 0x80),
                (byte) ((bits >> 42) & 0x7f)
            };
        case 8:
            return new byte[]
            {
                (byte) ((bits >> 0) & 0x7f | 0x80),
                (byte) ((bits >> 7) & 0x7f | 0x80),
                (byte) ((bits >> 14) & 0x7f | 0x80),
                (byte) ((bits >> 21) & 0x7f | 0x80),
                (byte) ((bits >> 28) & 0x7f | 0x80),
                (byte) ((bits >> 35) & 0x7f | 0x80),
                (byte) ((bits >> 42) & 0x7f | 0x80),
                (byte) ((bits >> 49) & 0x7f),
            };
        case 9:
            return new byte[]
            {
                (byte) ((bits >> 0) & 0x7f | 0x80),
                (byte) ((bits >> 7) & 0x7f | 0x80),
                (byte) ((bits >> 14) & 0x7f | 0x80),
                (byte) ((bits >> 21) & 0x7f | 0x80),
                (byte) ((bits >> 28) & 0x7f | 0x80),
                (byte) ((bits >> 35) & 0x7f | 0x80),
                (byte) ((bits >> 42) & 0x7f | 0x80),
                (byte) ((bits >> 49) & 0x7f | 0x80),
                (byte) ((bits >> 56) & 0x7f),
            };
        default:
            return new byte[]
            {
                (byte) ((bits >> 0) & 0x7f | 0x80),
                (byte) ((bits >> 7) & 0x7f | 0x80),
                (byte) ((bits >> 14) & 0x7f | 0x80),
                (byte) ((bits >> 21) & 0x7f | 0x80),
                (byte) ((bits >> 28) & 0x7f | 0x80),
                (byte) ((bits >> 35) & 0x7f | 0x80),
                (byte) ((bits >> 42) & 0x7f | 0x80),
                (byte) ((bits >> 49) & 0x7f | 0x80),
                (byte) ((bits >> 56) & 0x7f | 0x80),
                (byte) ((bits >> 63) & 0x01)
            };
        }
    }

    public abstract static class KafkaHeadersBuilder<T>
    {
        private final KafkaHeadersFW.Builder headersRW = new KafkaHeadersFW.Builder();
        private final DirectBuffer nameRO = new UnsafeBuffer(0, 0);
        private final DirectBuffer valueRO = new UnsafeBuffer(0, 0);

        private KafkaHeadersBuilder(
            String name)
        {
            MutableDirectBuffer buffer = new UnsafeBuffer(new byte[1024]);
            headersRW.wrap(buffer, 0, buffer.capacity());
            nameRO.wrap(name.getBytes(UTF_8));

            headersRW.nameLen(nameRO.capacity())
                     .name(nameRO, 0, nameRO.capacity());
        }

        public KafkaHeadersBuilder<T> sequence(
            String... values)
        {
            for (String value : values)
            {
                valueRO.wrap(value.getBytes(UTF_8));
                headersRW.valuesItem(vi -> vi.value(vb -> vb.length(valueRO.capacity())
                                                            .value(valueRO, 0, valueRO.capacity())));
            }
            return this;
        }

        public KafkaHeadersBuilder<T> skip(
            int count)
        {
            for (int i = 0; i < count; i++)
            {
                headersRW.valuesItem(vi -> vi.skip(sb -> sb.set(KafkaSkip.SKIP)));
            }
            return this;
        }

        public KafkaHeadersBuilder<T> skipMany()
        {
            headersRW.valuesItem(vi -> vi.skip(sb -> sb.set(KafkaSkip.SKIP_MANY)));
            return this;
        }

        public T build()
        {
            final KafkaHeadersFW headers = headersRW.build();
            return build(headers);
        }

        protected abstract T build(
            KafkaHeadersFW headers);


        protected void set(
            KafkaConditionFW.Builder builder,
            KafkaHeadersFW headers)
        {
            final OctetsFW name = headers.name();
            final int length = headers.nameLen();
            final Array32FW<KafkaValueMatchFW> values = headers.values();
            builder.headers(hb -> set(hb, name, length, values));
        }

        private void set(
            KafkaHeadersFW.Builder builder,
            OctetsFW name,
            int length,
            Array32FW<KafkaValueMatchFW> values)
        {
            builder.nameLen(length)
                   .name(name);
            values.forEach(v -> builder.valuesItem(vb -> set(vb, v)));
        }

        private void set(
            KafkaValueMatchFW.Builder builder,
            KafkaValueMatchFW valueMatch)
        {
            switch (valueMatch.kind())
            {
            case KafkaValueMatchFW.KIND_VALUE:
                final KafkaValueFW value = valueMatch.value();
                builder.value(vb -> vb.length(value.length())
                                      .value(value.value()));
                break;
            case KafkaValueMatchFW.KIND_SKIP:
                final KafkaSkipFW skip = valueMatch.skip();
                builder.skip(s -> s.set(skip.get()));
                break;
            }
        }
    }

    public abstract static class KafkaFilterBuilder<T>
    {
        private final KafkaFilterFW.Builder filterRW = new KafkaFilterFW.Builder();
        private final DirectBuffer keyRO = new UnsafeBuffer(0, 0);
        private final DirectBuffer nameRO = new UnsafeBuffer(0, 0);
        private final DirectBuffer valueRO = new UnsafeBuffer(0, 0);

        private KafkaFilterBuilder()
        {
            MutableDirectBuffer filterBuffer = new UnsafeBuffer(new byte[1024]);
            filterRW.wrap(filterBuffer, 0, filterBuffer.capacity());
        }

        public KafkaFilterBuilder<T> key(
            String key)
        {
            if (key != null)
            {
                keyRO.wrap(key.getBytes(UTF_8));
                filterRW.conditionsItem(c -> c.key(k -> k.length(keyRO.capacity())
                                                         .value(keyRO, 0, keyRO.capacity())));
            }
            else
            {
                filterRW.conditionsItem(c -> c.key(k -> k.length(-1)
                                                         .value((OctetsFW) null)));
            }
            return this;
        }

        public KafkaFilterBuilder<T> header(
            String name,
            String value)
        {
            if (value == null)
            {
                nameRO.wrap(name.getBytes(UTF_8));
                filterRW.conditionsItem(c -> c.header(h -> h.nameLen(nameRO.capacity())
                                                            .name(nameRO, 0, nameRO.capacity())
                                                            .valueLen(-1)
                                                            .value((OctetsFW) null)));
            }
            else
            {
                nameRO.wrap(name.getBytes(UTF_8));
                valueRO.wrap(value.getBytes(UTF_8));
                filterRW.conditionsItem(c -> c.header(h -> h.nameLen(nameRO.capacity())
                                                            .name(nameRO, 0, nameRO.capacity())
                                                            .valueLen(valueRO.capacity())
                                                            .value(valueRO, 0, valueRO.capacity())));
            }
            return this;
        }

        public KafkaHeadersBuilder<KafkaFilterBuilder<T>> headers(
            String name)
        {
            return new KafkaHeadersBuilder<>(name)
            {
                @Override
                protected KafkaFilterBuilder<T> build(
                    KafkaHeadersFW headers)
                {
                    filterRW.conditionsItem(ci -> set(ci, headers));
                    return KafkaFilterBuilder.this;
                }
            };
        }

        public KafkaFilterBuilder<T> keyNot(
            String key)
        {
            if (key != null)
            {
                keyRO.wrap(key.getBytes(UTF_8));
                filterRW.conditionsItem(i -> i.not(n -> n.condition(c -> c.key(k -> k.length(keyRO.capacity())
                                                                                     .value(keyRO, 0, keyRO.capacity())))));
            }
            else
            {
                filterRW.conditionsItem(i -> i.not(n -> n.condition(c -> c.key(k -> k.length(-1)
                                                                                     .value((OctetsFW) null)))));
            }
            return this;
        }

        public KafkaFilterBuilder<T> headerNot(
            String name,
            String value)
        {
            if (value == null)
            {
                nameRO.wrap(name.getBytes(UTF_8));
                filterRW.conditionsItem(i -> i.not(n -> n.condition(c -> c.header(h -> h.nameLen(nameRO.capacity())
                                                                                        .name(nameRO, 0, nameRO.capacity())
                                                                                        .valueLen(-1)
                                                                                        .value((OctetsFW) null)))));
            }
            else
            {
                nameRO.wrap(name.getBytes(UTF_8));
                valueRO.wrap(value.getBytes(UTF_8));
                filterRW.conditionsItem(i -> i.not(n -> n.condition(c -> c.header(h ->
                                                                            h.nameLen(nameRO.capacity())
                                                                              .name(nameRO, 0, nameRO.capacity())
                                                                              .valueLen(valueRO.capacity())
                                                                              .value(valueRO, 0, valueRO.capacity())))));
            }
            return this;
        }

        public T build()
        {
            final KafkaFilterFW filter = filterRW.build();
            return build(filter);
        }

        protected abstract T build(
            KafkaFilterFW filter);

        protected void set(
            KafkaFilterFW.Builder builder,
            KafkaFilterFW filter)
        {
            final Array32FW<KafkaConditionFW> conditions = filter.conditions();
            builder.conditions(csb -> set(csb, conditions));
        }

        private void set(
            Array32FW.Builder<KafkaConditionFW.Builder, KafkaConditionFW> builder,
            Array32FW<KafkaConditionFW> conditions)
        {
            conditions.forEach(c -> builder.item(ib -> set(ib, c)));
        }

        private void set(
            KafkaConditionFW.Builder builder,
            KafkaConditionFW condition)
        {
            switch (condition.kind())
            {
            case KafkaConditionFW.KIND_KEY:
                final KafkaKeyFW key = condition.key();
                builder.key(kb -> kb.length(key.length())
                                    .value(key.value()));
                break;
            case KafkaConditionFW.KIND_HEADER:
                final KafkaHeaderFW header = condition.header();
                builder.header(hb -> hb.nameLen(header.nameLen())
                                       .name(header.name())
                                       .valueLen(header.valueLen())
                                       .value(header.value()));
                break;
            case KafkaConditionFW.KIND_NOT:
                final KafkaNotFW not = condition.not();
                final KafkaConditionFW notCondition = not.condition();

                switch (notCondition.kind())
                {
                case KafkaConditionFW.KIND_KEY:
                    final KafkaKeyFW notKey = notCondition.key();
                    builder.not(n -> n.condition(c -> c.key(kb -> kb.length(notKey.length())
                                                                    .value(notKey.value()))));
                    break;
                case KafkaConditionFW.KIND_HEADER:
                    final KafkaHeaderFW notHeader = notCondition.header();
                    builder.not(n -> n.condition(c -> c.header(hb -> hb.nameLen(notHeader.nameLen())
                                                                       .name(notHeader.name())
                                                                       .valueLen(notHeader.valueLen())
                                                                       .value(notHeader.value()))));
                    break;
                }
                break;
            case KafkaConditionFW.KIND_HEADERS:
                final KafkaHeadersFW headers = condition.headers();
                final Array32FW<KafkaValueMatchFW> values = headers.values();
                builder.headers(hb -> hb.nameLen(headers.nameLen())
                                        .name(headers.name())
                                        .values(values));
                break;
            }
        }
    }

    public static final class KafkaBeginExBuilder
    {
        private final MutableDirectBuffer writeBuffer = new UnsafeBuffer(new byte[1024 * 8]);

        private final KafkaBeginExFW beginExRO = new KafkaBeginExFW();

        private final KafkaBeginExFW.Builder beginExRW = new KafkaBeginExFW.Builder();

        private KafkaBeginExBuilder()
        {
            beginExRW.wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public KafkaBeginExBuilder typeId(
            int typeId)
        {
            beginExRW.typeId(typeId);
            return this;
        }

        public KafkaBootstrapBeginExBuilder bootstrap()
        {
            beginExRW.kind(KafkaApi.BOOTSTRAP.value());

            return new KafkaBootstrapBeginExBuilder();
        }

        public KafkaMergedBeginExBuilder merged()
        {
            beginExRW.kind(KafkaApi.MERGED.value());

            return new KafkaMergedBeginExBuilder();
        }

        public KafkaFetchBeginExBuilder fetch()
        {
            beginExRW.kind(KafkaApi.FETCH.value());

            return new KafkaFetchBeginExBuilder();
        }

        public KafkaMetaBeginExBuilder meta()
        {
            beginExRW.kind(KafkaApi.META.value());

            return new KafkaMetaBeginExBuilder();
        }

        public KafkaDescribeBeginExBuilder describe()
        {
            beginExRW.kind(KafkaApi.DESCRIBE.value());

            return new KafkaDescribeBeginExBuilder();
        }

        public KafkaProduceBeginExBuilder produce()
        {
            beginExRW.kind(KafkaApi.PRODUCE.value());

            return new KafkaProduceBeginExBuilder();
        }

        public KafkaGroupBeginExBuilder group()
        {
            beginExRW.kind(KafkaApi.GROUP.value());

            return new KafkaGroupBeginExBuilder();
        }

        public KafkaConsumerBeginExBuilder consumer()
        {
            beginExRW.kind(KafkaApi.CONSUMER.value());

            return new KafkaConsumerBeginExBuilder();
        }

        public KafkaOffsetFetchBeginExBuilder offsetFetch()
        {
            beginExRW.kind(KafkaApi.OFFSET_FETCH.value());

            return new KafkaOffsetFetchBeginExBuilder();
        }

        public KafkaOffsetCommitBeginExBuilder offsetCommit()
        {
            beginExRW.kind(KafkaApi.OFFSET_COMMIT.value());

            return new KafkaOffsetCommitBeginExBuilder();
        }

        public byte[] build()
        {
            final KafkaBeginExFW beginEx = beginExRO;
            final byte[] array = new byte[beginEx.sizeof()];
            beginEx.buffer().getBytes(beginEx.offset(), array);
            return array;
        }

        public final class KafkaBootstrapBeginExBuilder
        {
            private final KafkaBootstrapBeginExFW.Builder bootstrapBeginExRW = new KafkaBootstrapBeginExFW.Builder();

            private KafkaBootstrapBeginExBuilder()
            {
                bootstrapBeginExRW.wrap(writeBuffer, KafkaBeginExFW.FIELD_OFFSET_BOOTSTRAP, writeBuffer.capacity());
            }

            public KafkaBootstrapBeginExBuilder topic(
                String topic)
            {
                bootstrapBeginExRW.topic(topic);
                return this;
            }

            public KafkaBeginExBuilder build()
            {
                final KafkaBootstrapBeginExFW bootstrapBeginEx = bootstrapBeginExRW.build();
                beginExRO.wrap(writeBuffer, 0, bootstrapBeginEx.limit());
                return KafkaBeginExBuilder.this;
            }
        }

        public final class KafkaMergedBeginExBuilder
        {
            private final KafkaMergedBeginExFW.Builder mergedBeginExRW = new KafkaMergedBeginExFW.Builder();

            private KafkaMergedBeginExBuilder()
            {
                mergedBeginExRW.wrap(writeBuffer, KafkaBeginExFW.FIELD_OFFSET_MERGED, writeBuffer.capacity());
            }

            public KafkaMergedBeginExBuilder capabilities(
                String capabilities)
            {
                mergedBeginExRW.capabilities(c -> c.set(KafkaCapabilities.valueOf(capabilities)));
                return this;
            }

            public KafkaMergedBeginExBuilder topic(
                String topic)
            {
                mergedBeginExRW.topic(topic);
                return this;
            }

            public KafkaMergedBeginExBuilder groupId(
                String groupId)
            {
                mergedBeginExRW.groupId(groupId);
                return this;
            }

            public KafkaMergedBeginExBuilder consumerId(
                String consumerId)
            {
                mergedBeginExRW.consumerId(consumerId);
                return this;
            }

            public KafkaMergedBeginExBuilder partition(
                int partitionId,
                long offset)
            {
                partition(partitionId, offset, DEFAULT_LATEST_OFFSET);
                return this;
            }

            public KafkaMergedBeginExBuilder partition(
                int partitionId,
                long offset,
                long latestOffset)
            {
                partition(partitionId, offset, latestOffset, latestOffset);
                return this;
            }

            public KafkaMergedBeginExBuilder partition(
                int partitionId,
                long offset,
                long stableOffset,
                long latestOffset)
            {
                mergedBeginExRW.partitionsItem(p -> p.partitionId(partitionId)
                                                     .partitionOffset(offset)
                                                     .stableOffset(stableOffset)
                                                     .latestOffset(latestOffset));
                return this;
            }

            public KafkaFilterBuilder<KafkaMergedBeginExBuilder> filter()
            {
                return new KafkaFilterBuilder<>()
                {

                    @Override
                    protected KafkaMergedBeginExBuilder build(
                        KafkaFilterFW filter)
                    {
                        mergedBeginExRW.filtersItem(fb -> set(fb, filter));
                        return KafkaMergedBeginExBuilder.this;
                    }
                };
            }

            public KafkaMergedBeginExBuilder isolation(
                String isolation)
            {
                mergedBeginExRW.isolation(d -> d.set(KafkaIsolation.valueOf(isolation)));
                return this;
            }

            public KafkaMergedBeginExBuilder deltaType(
                String delta)
            {
                mergedBeginExRW.deltaType(d -> d.set(KafkaDeltaType.valueOf(delta)));
                return this;
            }

            public KafkaMergedBeginExBuilder evaluation(
                String evaluation)
            {
                mergedBeginExRW.evaluation(d -> d.set(KafkaEvaluation.valueOf(evaluation)));
                return this;
            }

            public KafkaMergedBeginExBuilder ackMode(
                String ackMode)
            {
                mergedBeginExRW.ackMode(a -> a.set(KafkaAckMode.valueOf(ackMode)));
                return this;
            }

            public KafkaBeginExBuilder build()
            {
                final KafkaMergedBeginExFW mergedBeginEx = mergedBeginExRW.build();
                beginExRO.wrap(writeBuffer, 0, mergedBeginEx.limit());
                return KafkaBeginExBuilder.this;
            }
        }

        public final class KafkaFetchBeginExBuilder
        {
            private final KafkaFetchBeginExFW.Builder fetchBeginExRW = new KafkaFetchBeginExFW.Builder();

            private KafkaFetchBeginExBuilder()
            {
                fetchBeginExRW.wrap(writeBuffer, KafkaBeginExFW.FIELD_OFFSET_FETCH, writeBuffer.capacity());
            }

            public KafkaFetchBeginExBuilder topic(
                String topic)
            {
                fetchBeginExRW.topic(topic);
                return this;
            }

            public KafkaFetchBeginExBuilder partition(
                int partitionId,
                long offset)
            {
                partition(partitionId, offset, DEFAULT_LATEST_OFFSET);
                return this;
            }

            public KafkaFetchBeginExBuilder partition(
                int partitionId,
                long offset,
                long latestOffset)
            {
                partition(partitionId, offset, latestOffset, latestOffset);
                return this;
            }

            public KafkaFetchBeginExBuilder partition(
                int partitionId,
                long offset,
                long stableOffset,
                long latestOffset)
            {
                fetchBeginExRW.partition(p -> p.partitionId(partitionId)
                                               .partitionOffset(offset)
                                               .stableOffset(stableOffset)
                                               .latestOffset(latestOffset));
                return this;
            }

            public KafkaFilterBuilder<KafkaFetchBeginExBuilder> filter()
            {
                return new KafkaFilterBuilder<>()
                {

                    @Override
                    protected KafkaFetchBeginExBuilder build(
                        KafkaFilterFW filter)
                    {
                        fetchBeginExRW.filtersItem(fb -> set(fb, filter));
                        return KafkaFetchBeginExBuilder.this;
                    }
                };
            }

            public KafkaFetchBeginExBuilder isolation(
                String isolation)
            {
                fetchBeginExRW.isolation(d -> d.set(KafkaIsolation.valueOf(isolation)));
                return this;
            }

            public KafkaFetchBeginExBuilder deltaType(
                String deltaType)
            {
                fetchBeginExRW.deltaType(d -> d.set(KafkaDeltaType.valueOf(deltaType)));
                return this;
            }

            public KafkaFetchBeginExBuilder evaluation(
                String evaluation)
            {
                fetchBeginExRW.evaluation(d -> d.set(KafkaEvaluation.valueOf(evaluation)));
                return this;
            }

            public KafkaBeginExBuilder build()
            {
                final KafkaFetchBeginExFW fetchBeginEx = fetchBeginExRW.build();
                beginExRO.wrap(writeBuffer, 0, fetchBeginEx.limit());
                return KafkaBeginExBuilder.this;
            }
        }

        public final class KafkaMetaBeginExBuilder
        {
            private final KafkaMetaBeginExFW.Builder metaBeginExRW = new KafkaMetaBeginExFW.Builder();

            private KafkaMetaBeginExBuilder()
            {
                metaBeginExRW.wrap(writeBuffer, KafkaBeginExFW.FIELD_OFFSET_META, writeBuffer.capacity());
            }

            public KafkaMetaBeginExBuilder topic(
                String topic)
            {
                metaBeginExRW.topic(topic);
                return this;
            }

            public KafkaBeginExBuilder build()
            {
                final KafkaMetaBeginExFW metaBeginEx = metaBeginExRW.build();
                beginExRO.wrap(writeBuffer, 0, metaBeginEx.limit());
                return KafkaBeginExBuilder.this;
            }
        }

        public final class KafkaDescribeBeginExBuilder
        {
            private final KafkaDescribeBeginExFW.Builder describeBeginExRW = new KafkaDescribeBeginExFW.Builder();

            private KafkaDescribeBeginExBuilder()
            {
                describeBeginExRW.wrap(writeBuffer, KafkaBeginExFW.FIELD_OFFSET_DESCRIBE, writeBuffer.capacity());
            }

            public KafkaDescribeBeginExBuilder topic(
                String name)
            {
                describeBeginExRW.topic(name);
                return this;
            }

            public KafkaDescribeBeginExBuilder config(
                String name)
            {
                describeBeginExRW.configsItem(c -> c.set(name, UTF_8));
                return this;
            }

            public KafkaBeginExBuilder build()
            {
                final KafkaDescribeBeginExFW describeBeginEx = describeBeginExRW.build();
                beginExRO.wrap(writeBuffer, 0, describeBeginEx.limit());
                return KafkaBeginExBuilder.this;
            }
        }

        public final class KafkaProduceBeginExBuilder
        {
            private final KafkaProduceBeginExFW.Builder produceBeginExRW = new KafkaProduceBeginExFW.Builder();

            private boolean transactionSet;

            private KafkaProduceBeginExBuilder()
            {
                produceBeginExRW.wrap(writeBuffer, KafkaBeginExFW.FIELD_OFFSET_PRODUCE, writeBuffer.capacity());
            }

            public KafkaProduceBeginExBuilder transaction(
                String transaction)
            {
                produceBeginExRW.transaction(transaction);
                transactionSet = true;
                return this;
            }

            public KafkaProduceBeginExBuilder producerId(
                long producerId)
            {
                ensureTransactionSet();
                produceBeginExRW.producerId(producerId);
                return this;
            }

            public KafkaProduceBeginExBuilder topic(
                String topic)
            {
                ensureTransactionSet();
                produceBeginExRW.topic(topic);
                return this;
            }

            public KafkaProduceBeginExBuilder partition(
                int partitionId)
            {
                partition(partitionId, DEFAULT_LATEST_OFFSET);
                return this;
            }

            public KafkaProduceBeginExBuilder partition(
                int partitionId,
                long partitionOffset)
            {
                partition(partitionId, partitionOffset, DEFAULT_LATEST_OFFSET);
                return this;
            }

            public KafkaProduceBeginExBuilder partition(
                int partitionId,
                long partitionOffset,
                long latestOffset)
            {
                produceBeginExRW.partition(p -> p.partitionId(partitionId)
                                                 .partitionOffset(partitionOffset)
                                                 .latestOffset(latestOffset));
                return this;
            }

            public KafkaBeginExBuilder build()
            {
                final KafkaProduceBeginExFW produceBeginEx = produceBeginExRW.build();
                beginExRO.wrap(writeBuffer, 0, produceBeginEx.limit());
                return KafkaBeginExBuilder.this;
            }

            private void ensureTransactionSet()
            {
                if (!transactionSet)
                {
                    transaction(null);
                }
            }
        }

        public final class KafkaGroupBeginExBuilder
        {
            private final KafkaGroupBeginExFW.Builder groupBeginExRW = new KafkaGroupBeginExFW.Builder();


            private KafkaGroupBeginExBuilder()
            {
                groupBeginExRW.wrap(writeBuffer, KafkaBeginExFW.FIELD_OFFSET_PRODUCE, writeBuffer.capacity());
            }

            public KafkaGroupBeginExBuilder groupId(
                String groupId)
            {
                groupBeginExRW.groupId(groupId);
                return this;
            }

            public KafkaGroupBeginExBuilder protocol(
                String protocol)
            {
                groupBeginExRW.protocol(protocol);
                return this;
            }

            public KafkaGroupBeginExBuilder timeout(
                int timeout)
            {
                groupBeginExRW.timeout(timeout);
                return this;
            }

            public KafkaGroupBeginExBuilder metadata(
                String consumerId,
                String topic,
                int partitionId)
            {
                groupBeginExRW.metadata(m -> m.consumerId(consumerId)
                    .topics(t -> t.item(tp -> tp.topic(topic)
                        .partitions(p -> p.item(pi -> pi.partitionId(partitionId))))));
                return this;
            }

            public KafkaBeginExBuilder build()
            {
                final KafkaGroupBeginExFW groupBeginEx = groupBeginExRW.build();
                beginExRO.wrap(writeBuffer, 0, groupBeginEx.limit());
                return KafkaBeginExBuilder.this;
            }
        }

        public final class KafkaConsumerBeginExBuilder
        {
            private final KafkaConsumerBeginExFW.Builder consumerBeginExRW = new KafkaConsumerBeginExFW.Builder();


            private KafkaConsumerBeginExBuilder()
            {
                consumerBeginExRW.wrap(writeBuffer, KafkaBeginExFW.FIELD_OFFSET_CONSUMER, writeBuffer.capacity());
            }

            public KafkaConsumerBeginExBuilder groupId(
                String groupId)
            {
                consumerBeginExRW.groupId(groupId);
                return this;
            }

            public KafkaConsumerBeginExBuilder consumerId(
                String consumerId)
            {
                consumerBeginExRW.consumerId(consumerId);
                return this;
            }

            public KafkaConsumerBeginExBuilder timeout(
                int timeout)
            {
                consumerBeginExRW.timeout(timeout);
                return this;
            }

            public KafkaConsumerBeginExBuilder topic(
                String topic)
            {
                consumerBeginExRW.topic(topic);
                return this;
            }

            public KafkaConsumerBeginExBuilder partition(
                int partitionId)
            {
                consumerBeginExRW.partitionIds(p -> p.item(i -> i.partitionId(partitionId)));
                return this;
            }

            public KafkaBeginExBuilder build()
            {
                final KafkaConsumerBeginExFW consumerBeginEx = consumerBeginExRW.build();
                beginExRO.wrap(writeBuffer, 0, consumerBeginEx.limit());
                return KafkaBeginExBuilder.this;
            }
        }

        public final class KafkaOffsetFetchBeginExBuilder
        {
            private final KafkaOffsetFetchBeginExFW.Builder offsetFetchBeginExRW = new KafkaOffsetFetchBeginExFW.Builder();


            private KafkaOffsetFetchBeginExBuilder()
            {
                offsetFetchBeginExRW.wrap(writeBuffer, KafkaBeginExFW.FIELD_OFFSET_OFFSET_FETCH, writeBuffer.capacity());
            }

            public KafkaOffsetFetchBeginExBuilder groupId(
                String groupId)
            {
                offsetFetchBeginExRW.groupId(groupId);
                return this;
            }

            public KafkaOffsetFetchBeginExBuilder topic(
                String topic,
                int partitionId)
            {
                offsetFetchBeginExRW.topics(t -> t.item(i ->
                    i.topic(topic)
                        .partitions(p -> p.item(a -> a.partitionId(partitionId)))));
                return this;
            }

            public KafkaBeginExBuilder build()
            {
                final KafkaOffsetFetchBeginExFW consumerBeginEx = offsetFetchBeginExRW.build();
                beginExRO.wrap(writeBuffer, 0, consumerBeginEx.limit());
                return KafkaBeginExBuilder.this;
            }
        }

        public final class KafkaOffsetCommitBeginExBuilder
        {
            private final KafkaOffsetCommitBeginExFW.Builder offsetCommitBeginExRW = new KafkaOffsetCommitBeginExFW.Builder();


            private KafkaOffsetCommitBeginExBuilder()
            {
                offsetCommitBeginExRW.wrap(writeBuffer, KafkaBeginExFW.FIELD_OFFSET_OFFSET_COMMIT, writeBuffer.capacity());
            }

            public KafkaOffsetCommitBeginExBuilder groupId(
                String groupId)
            {
                offsetCommitBeginExRW.groupId(groupId);
                return this;
            }

            public KafkaOffsetCommitBeginExBuilder topic(
                String topic)
            {
                offsetCommitBeginExRW.topic(topic);
                return this;
            }

            public KafkaBeginExBuilder build()
            {
                final KafkaOffsetCommitBeginExFW offsetCommitBeginEx = offsetCommitBeginExRW.build();
                beginExRO.wrap(writeBuffer, 0, offsetCommitBeginEx.limit());
                return KafkaBeginExBuilder.this;
            }
        }
    }

    public static final class KafkaDataExBuilder
    {
        private final MutableDirectBuffer writeBuffer = new UnsafeBuffer(new byte[1024 * 8]);

        private final KafkaDataExFW dataExRO = new KafkaDataExFW();

        private final KafkaDataExFW.Builder dataExRW = new KafkaDataExFW.Builder();

        private KafkaDataExBuilder()
        {
            dataExRW.wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public KafkaDataExBuilder typeId(
            int typeId)
        {
            dataExRW.typeId(typeId);
            return this;
        }

        public KafkaMergedDataExBuilder merged()
        {
            dataExRW.kind(KafkaApi.MERGED.value());

            return new KafkaMergedDataExBuilder();
        }

        public KafkaFetchDataExBuilder fetch()
        {
            dataExRW.kind(KafkaApi.FETCH.value());

            return new KafkaFetchDataExBuilder();
        }

        public KafkaMetaDataExBuilder meta()
        {
            dataExRW.kind(KafkaApi.META.value());

            return new KafkaMetaDataExBuilder();
        }

        public KafkaDescribeDataExBuilder describe()
        {
            dataExRW.kind(KafkaApi.DESCRIBE.value());

            return new KafkaDescribeDataExBuilder();
        }

        public KafkaProduceDataExBuilder produce()
        {
            dataExRW.kind(KafkaApi.PRODUCE.value());

            return new KafkaProduceDataExBuilder();
        }

        public KafkaConsumerDataExBuilder consumer()
        {
            dataExRW.kind(KafkaApi.CONSUMER.value());

            return new KafkaConsumerDataExBuilder();
        }

        public KafkaOffsetFetchDataExBuilder offsetFetch()
        {
            dataExRW.kind(KafkaApi.OFFSET_FETCH.value());

            return new KafkaOffsetFetchDataExBuilder();
        }

        public KafkaOffsetCommitDataExBuilder offsetCommit()
        {
            dataExRW.kind(KafkaApi.OFFSET_COMMIT.value());

            return new KafkaOffsetCommitDataExBuilder();
        }

        public byte[] build()
        {
            final KafkaDataExFW dataEx = dataExRO;
            final byte[] array = new byte[dataEx.sizeof()];
            dataEx.buffer().getBytes(dataEx.offset(), array);
            return array;
        }
        public final class KafkaFetchDataExBuilder
        {
            private final DirectBuffer keyRO = new UnsafeBuffer(0, 0);
            private final DirectBuffer nameRO = new UnsafeBuffer(0, 0);
            private final DirectBuffer valueRO = new UnsafeBuffer(0, 0);

            private final KafkaFetchDataExFW.Builder fetchDataExRW = new KafkaFetchDataExFW.Builder();

            private KafkaFetchDataExBuilder()
            {
                fetchDataExRW.wrap(writeBuffer, KafkaDataExFW.FIELD_OFFSET_FETCH, writeBuffer.capacity());
            }

            public KafkaFetchDataExBuilder deferred(
                int deferred)
            {
                fetchDataExRW.deferred(deferred);
                return this;
            }

            public KafkaFetchDataExBuilder timestamp(
                long timestamp)
            {
                fetchDataExRW.timestamp(timestamp);
                return this;
            }

            public KafkaFetchDataExBuilder filters(
                long filters)
            {
                fetchDataExRW.filters(filters);
                return this;
            }

            public KafkaFetchDataExBuilder producerId(
                long timestamp)
            {
                fetchDataExRW.producerId(timestamp);
                return this;
            }

            public KafkaFetchDataExBuilder partition(
                int partitionId,
                long partitionOffset)
            {
                fetchDataExRW.partition(p -> p.partitionId(partitionId).partitionOffset(partitionOffset));
                return this;
            }

            public KafkaFetchDataExBuilder partition(
                int partitionId,
                long partitionOffset,
                long latestOffset)
            {
                fetchDataExRW.partition(p -> p.partitionId(partitionId)
                                              .partitionOffset(partitionOffset)
                                              .stableOffset(latestOffset)
                                              .latestOffset(latestOffset));
                return this;
            }
            public KafkaFetchDataExBuilder partition(
                int partitionId,
                long partitionOffset,
                long stableOffset,
                long latestOffset)
            {
                fetchDataExRW.partition(p -> p.partitionId(partitionId)
                                              .partitionOffset(partitionOffset)
                                              .stableOffset(stableOffset)
                                              .latestOffset(latestOffset));
                return this;
            }

            public KafkaFetchDataExBuilder key(
                String key)
            {
                if (key == null)
                {
                    fetchDataExRW.key(m -> m.length(-1)
                                            .value((OctetsFW) null));
                }
                else
                {
                    keyRO.wrap(key.getBytes(UTF_8));
                    fetchDataExRW.key(k -> k.length(keyRO.capacity())
                                            .value(keyRO, 0, keyRO.capacity()));
                }
                return this;
            }

            public KafkaFetchDataExBuilder delta(
                String deltaType,
                long ancestorOffset)
            {
                fetchDataExRW.delta(d -> d.type(t -> t.set(KafkaDeltaType.valueOf(deltaType)))
                                          .ancestorOffset(ancestorOffset));
                return this;
            }

            public KafkaFetchDataExBuilder header(
                String name,
                String value)
            {
                if (value == null)
                {
                    nameRO.wrap(name.getBytes(UTF_8));
                    fetchDataExRW.headersItem(h -> h.nameLen(nameRO.capacity())
                                                    .name(nameRO, 0, nameRO.capacity())
                                                    .valueLen(-1)
                                                    .value((OctetsFW) null));
                }
                else
                {
                    nameRO.wrap(name.getBytes(UTF_8));
                    valueRO.wrap(value.getBytes(UTF_8));
                    fetchDataExRW.headersItem(h -> h.nameLen(nameRO.capacity())
                                                    .name(nameRO, 0, nameRO.capacity())
                                                    .valueLen(valueRO.capacity())
                                                    .value(valueRO, 0, valueRO.capacity()));
                }
                return this;
            }

            public KafkaDataExBuilder build()
            {
                final KafkaFetchDataExFW fetchDataEx = fetchDataExRW.build();
                dataExRO.wrap(writeBuffer, 0, fetchDataEx.limit());
                return KafkaDataExBuilder.this;
            }
        }

        public final class KafkaMergedDataExBuilder
        {
            private final DirectBuffer keyRO = new UnsafeBuffer(0, 0);
            private final DirectBuffer hashKeyRO = new UnsafeBuffer(0, 0);
            private final DirectBuffer nameRO = new UnsafeBuffer(0, 0);
            private final DirectBuffer valueRO = new UnsafeBuffer(0, 0);

            private final KafkaMergedDataExFW.Builder mergedDataExRW = new KafkaMergedDataExFW.Builder();

            private KafkaMergedDataExBuilder()
            {
                mergedDataExRW.wrap(writeBuffer, KafkaDataExFW.FIELD_OFFSET_MERGED, writeBuffer.capacity());
            }

            public KafkaMergedDataExBuilder deferred(
                int deferred)
            {
                mergedDataExRW.deferred(deferred);
                return this;
            }

            public KafkaMergedDataExBuilder timestamp(
                long timestamp)
            {
                mergedDataExRW.timestamp(timestamp);
                return this;
            }

            public KafkaMergedDataExBuilder filters(
                long filters)
            {
                mergedDataExRW.filters(filters);
                return this;
            }

            public KafkaMergedDataExBuilder partition(
                int partitionId,
                long partitionOffset)
            {
                partition(partitionId, partitionOffset, DEFAULT_LATEST_OFFSET);
                return this;
            }

            public KafkaMergedDataExBuilder partition(
                int partitionId,
                long partitionOffset,
                long latestOffset)
            {
                mergedDataExRW.partition(p -> p
                    .partitionId(partitionId)
                    .partitionOffset(partitionOffset)
                    .latestOffset(latestOffset));
                return this;
            }

            public KafkaMergedDataExBuilder progress(
                int partitionId,
                long partitionOffset)
            {
                progress(partitionId, partitionOffset, DEFAULT_LATEST_OFFSET);
                return this;
            }

            public KafkaMergedDataExBuilder progress(
                int partitionId,
                long partitionOffset,
                long latestOffset)
            {
                mergedDataExRW.progressItem(p -> p
                    .partitionId(partitionId)
                    .partitionOffset(partitionOffset)
                    .latestOffset(latestOffset));
                return this;
            }

            public KafkaMergedDataExBuilder key(
                String key)
            {
                if (key == null)
                {
                    mergedDataExRW.key(m -> m.length(-1)
                                            .value((OctetsFW) null));
                }
                else
                {
                    keyRO.wrap(key.getBytes(UTF_8));
                    mergedDataExRW.key(k -> k.length(keyRO.capacity())
                                            .value(keyRO, 0, keyRO.capacity()));
                }
                return this;
            }

            public KafkaMergedDataExBuilder hashKey(
                String hashKey)
            {
                if (hashKey == null)
                {
                    mergedDataExRW.hashKey(m -> m.length(-1)
                        .value((OctetsFW) null));
                }
                else
                {
                    hashKeyRO.wrap(hashKey.getBytes(UTF_8));
                    mergedDataExRW.hashKey(k -> k.length(hashKeyRO.capacity())
                        .value(hashKeyRO, 0, hashKeyRO.capacity()));
                }
                return this;
            }

            public KafkaMergedDataExBuilder delta(
                String deltaType,
                long ancestorOffset)
            {
                mergedDataExRW.delta(d -> d.type(t -> t.set(KafkaDeltaType.valueOf(deltaType)))
                                           .ancestorOffset(ancestorOffset));
                return this;
            }

            public KafkaMergedDataExBuilder header(
                String name,
                String value)
            {
                if (value == null)
                {
                    nameRO.wrap(name.getBytes(UTF_8));
                    mergedDataExRW.headersItem(h -> h.nameLen(nameRO.capacity())
                                                    .name(nameRO, 0, nameRO.capacity())
                                                    .valueLen(-1)
                                                    .value((OctetsFW) null));
                }
                else
                {
                    nameRO.wrap(name.getBytes(UTF_8));
                    valueRO.wrap(value.getBytes(UTF_8));
                    mergedDataExRW.headersItem(h -> h.nameLen(nameRO.capacity())
                                                    .name(nameRO, 0, nameRO.capacity())
                                                    .valueLen(valueRO.capacity())
                                                    .value(valueRO, 0, valueRO.capacity()));
                }
                return this;
            }

            public KafkaMergedDataExBuilder headerNull(
                String name)
            {
                nameRO.wrap(name.getBytes(UTF_8));
                mergedDataExRW.headersItem(h -> h.nameLen(nameRO.capacity())
                                                .name(nameRO, 0, nameRO.capacity())
                                                .valueLen(-1)
                                                .value((OctetsFW) null));
                return this;
            }

            public KafkaMergedDataExBuilder headerByte(
                String name,
                byte value)
            {
                nameRO.wrap(name.getBytes(UTF_8));
                valueRO.wrap(ByteBuffer.allocate(Byte.BYTES).put(value));
                mergedDataExRW.headersItem(h -> h.nameLen(nameRO.capacity())
                                                .name(nameRO, 0, nameRO.capacity())
                                                .valueLen(valueRO.capacity())
                                                .value(valueRO, 0, valueRO.capacity()));
                return this;
            }

            public KafkaMergedDataExBuilder headerShort(
                String name,
                short value)
            {
                nameRO.wrap(name.getBytes(UTF_8));
                valueRO.wrap(ByteBuffer.allocate(Short.BYTES).putShort(value));
                mergedDataExRW.headersItem(h -> h.nameLen(nameRO.capacity())
                                                .name(nameRO, 0, nameRO.capacity())
                                                .valueLen(valueRO.capacity())
                                                .value(valueRO, 0, valueRO.capacity()));
                return this;
            }

            public KafkaMergedDataExBuilder headerInt(
                String name,
                int value)
            {
                nameRO.wrap(name.getBytes(UTF_8));
                valueRO.wrap(ByteBuffer.allocate(Integer.BYTES).putInt(value));
                mergedDataExRW.headersItem(h -> h.nameLen(nameRO.capacity())
                                                .name(nameRO, 0, nameRO.capacity())
                                                .valueLen(valueRO.capacity())
                                                .value(valueRO, 0, valueRO.capacity()));
                return this;
            }

            public KafkaMergedDataExBuilder headerLong(
                String name,
                long value)
            {
                nameRO.wrap(name.getBytes(UTF_8));
                valueRO.wrap(ByteBuffer.allocate(Long.BYTES).putLong(value));
                mergedDataExRW.headersItem(h -> h.nameLen(nameRO.capacity())
                                                .name(nameRO, 0, nameRO.capacity())
                                                .valueLen(valueRO.capacity())
                                                .value(valueRO, 0, valueRO.capacity()));
                return this;
            }

            public KafkaMergedDataExBuilder headerBytes(
                String name,
                byte[] value)
            {
                if (value == null)
                {
                    nameRO.wrap(name.getBytes(UTF_8));
                    mergedDataExRW.headersItem(h -> h.nameLen(nameRO.capacity())
                                                     .name(nameRO, 0, nameRO.capacity())
                                                     .valueLen(-1)
                                                     .value((OctetsFW) null));
                }
                else
                {
                    nameRO.wrap(name.getBytes(UTF_8));
                    valueRO.wrap(value);
                    mergedDataExRW.headersItem(h -> h.nameLen(nameRO.capacity())
                                                     .name(nameRO, 0, nameRO.capacity())
                                                     .valueLen(valueRO.capacity())
                                                     .value(valueRO, 0, valueRO.capacity()));
                }
                return this;
            }

            public KafkaDataExBuilder build()
            {
                final KafkaMergedDataExFW mergedDataEx = mergedDataExRW.build();
                dataExRO.wrap(writeBuffer, 0, mergedDataEx.limit());
                return KafkaDataExBuilder.this;
            }
        }

        public final class KafkaMetaDataExBuilder
        {
            private final KafkaMetaDataExFW.Builder metaDataExRW = new KafkaMetaDataExFW.Builder();

            private KafkaMetaDataExBuilder()
            {
                metaDataExRW.wrap(writeBuffer, KafkaDataExFW.FIELD_OFFSET_META, writeBuffer.capacity());
            }

            public KafkaMetaDataExBuilder partition(
                int partitionId,
                int leaderId)
            {
                metaDataExRW.partitionsItem(p -> p.partitionId(partitionId).leaderId(leaderId));
                return this;
            }

            public KafkaDataExBuilder build()
            {
                final KafkaMetaDataExFW metaDataEx = metaDataExRW.build();
                dataExRO.wrap(writeBuffer, 0, metaDataEx.limit());
                return KafkaDataExBuilder.this;
            }
        }

        public final class KafkaDescribeDataExBuilder
        {
            private final KafkaDescribeDataExFW.Builder describeDataExRW = new KafkaDescribeDataExFW.Builder();

            private KafkaDescribeDataExBuilder()
            {
                describeDataExRW.wrap(writeBuffer, KafkaDataExFW.FIELD_OFFSET_DESCRIBE, writeBuffer.capacity());
            }

            public KafkaDescribeDataExBuilder config(
                String name,
                String value)
            {
                describeDataExRW.configsItem(c -> c.name(name).value(value));
                return this;
            }

            public KafkaDataExBuilder build()
            {
                final KafkaDescribeDataExFW describeDataEx = describeDataExRW.build();
                dataExRO.wrap(writeBuffer, 0, describeDataEx.limit());
                return KafkaDataExBuilder.this;
            }
        }

        public final class KafkaProduceDataExBuilder
        {
            private final DirectBuffer keyRO = new UnsafeBuffer(0, 0);
            private final DirectBuffer nameRO = new UnsafeBuffer(0, 0);
            private final DirectBuffer valueRO = new UnsafeBuffer(0, 0);

            private final KafkaProduceDataExFW.Builder produceDataExRW = new KafkaProduceDataExFW.Builder();

            private KafkaProduceDataExBuilder()
            {
                produceDataExRW.wrap(writeBuffer, KafkaDataExFW.FIELD_OFFSET_DESCRIBE, writeBuffer.capacity());
            }

            public KafkaProduceDataExBuilder deferred(
                int deferred)
            {
                produceDataExRW.deferred(deferred);
                return this;
            }

            public KafkaProduceDataExBuilder timestamp(
                long timestamp)
            {
                produceDataExRW.timestamp(timestamp);
                return this;
            }

            public KafkaProduceDataExBuilder sequence(
                int sequence)
            {
                produceDataExRW.sequence(sequence);
                return this;
            }

            public KafkaProduceDataExBuilder ackMode(
                String ackMode)
            {
                produceDataExRW.ackMode(a -> a.set(KafkaAckMode.valueOf(ackMode)));
                return this;
            }

            public KafkaProduceDataExBuilder key(
                String key)
            {
                if (key == null)
                {
                    produceDataExRW.key(m -> m.length(-1)
                                              .value((OctetsFW) null));
                }
                else
                {
                    keyRO.wrap(key.getBytes(UTF_8));
                    produceDataExRW.key(k -> k.length(keyRO.capacity())
                                              .value(keyRO, 0, keyRO.capacity()));
                }
                return this;
            }

            public KafkaProduceDataExBuilder header(
                String name,
                String value)
            {
                if (value == null)
                {
                    nameRO.wrap(name.getBytes(UTF_8));
                    produceDataExRW.headersItem(h -> h.nameLen(nameRO.capacity())
                                                      .name(nameRO, 0, nameRO.capacity())
                                                      .valueLen(-1)
                                                      .value((OctetsFW) null));
                }
                else
                {
                    nameRO.wrap(name.getBytes(UTF_8));
                    valueRO.wrap(value.getBytes(UTF_8));
                    produceDataExRW.headersItem(h -> h.nameLen(nameRO.capacity())
                                                      .name(nameRO, 0, nameRO.capacity())
                                                      .valueLen(valueRO.capacity())
                                                      .value(valueRO, 0, valueRO.capacity()));
                }
                return this;
            }

            public KafkaDataExBuilder build()
            {
                final KafkaProduceDataExFW produceDataEx = produceDataExRW.build();
                dataExRO.wrap(writeBuffer, 0, produceDataEx.limit());
                return KafkaDataExBuilder.this;
            }
        }

        public final class KafkaConsumerDataExBuilder
        {
            private final KafkaConsumerDataExFW.Builder consumerDataExRW = new KafkaConsumerDataExFW.Builder();

            private KafkaConsumerDataExBuilder()
            {
                consumerDataExRW.wrap(writeBuffer, KafkaDataExFW.FIELD_OFFSET_CONSUMER, writeBuffer.capacity());
            }

            public KafkaConsumerDataExBuilder partition(
                int partitionId)
            {
                consumerDataExRW.partitions(p -> p.item(i -> i.partitionId(partitionId)));
                return this;
            }

            public KafkaDataExBuilder build()
            {
                final KafkaConsumerDataExFW consumerDataEx = consumerDataExRW.build();
                dataExRO.wrap(writeBuffer, 0, consumerDataEx.limit());
                return KafkaDataExBuilder.this;
            }
        }

        public final class KafkaOffsetFetchDataExBuilder
        {
            private final KafkaOffsetFetchDataExFW.Builder offsetFetchDataExRW = new KafkaOffsetFetchDataExFW.Builder();

            private KafkaOffsetFetchDataExBuilder()
            {
                offsetFetchDataExRW.wrap(writeBuffer, KafkaDataExFW.FIELD_OFFSET_OFFSET_FETCH, writeBuffer.capacity());
            }

            public KafkaOffsetFetchDataExBuilder topic(
                String topic,
                int partitionId,
                long offset)
            {
                offsetFetchDataExRW.topic(t ->
                    t.topic(topic).offsets(o -> o.item(i ->
                        i.partitionId(partitionId).partitionOffset(offset))));
                return this;
            }

            public KafkaDataExBuilder build()
            {
                final KafkaOffsetFetchDataExFW offsetFetchDataEx = offsetFetchDataExRW.build();
                dataExRO.wrap(writeBuffer, 0, offsetFetchDataEx.limit());
                return KafkaDataExBuilder.this;
            }
        }

        public final class KafkaOffsetCommitDataExBuilder
        {
            private final KafkaOffsetCommitDataExFW.Builder offsetCommitDataExRW = new KafkaOffsetCommitDataExFW.Builder();

            private KafkaOffsetCommitDataExBuilder()
            {
                offsetCommitDataExRW.wrap(writeBuffer, KafkaDataExFW.FIELD_OFFSET_OFFSET_COMMIT, writeBuffer.capacity());
            }

            public KafkaOffsetCommitDataExBuilder partitionId(
                int partitionId)
            {
                offsetCommitDataExRW.partitionId(partitionId);
                return this;
            }

            public KafkaOffsetCommitDataExBuilder partitionOffset(
                long partitionId)
            {
                offsetCommitDataExRW.partitionOffset(partitionId);
                return this;
            }

            public KafkaDataExBuilder build()
            {
                final KafkaOffsetCommitDataExFW consumerDataEx = offsetCommitDataExRW.build();
                dataExRO.wrap(writeBuffer, 0, consumerDataEx.limit());
                return KafkaDataExBuilder.this;
            }
        }
    }

    public static final class KafkaFlushExBuilder
    {
        private final MutableDirectBuffer writeBuffer = new UnsafeBuffer(new byte[1024 * 8]);

        private final DirectBuffer keyRO = new UnsafeBuffer(0, 0);
        private final KafkaFlushExFW flushExRO = new KafkaFlushExFW();

        private final KafkaFlushExFW.Builder flushExRW = new KafkaFlushExFW.Builder();

        private KafkaFlushExBuilder()
        {
            flushExRW.wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public KafkaFlushExBuilder typeId(
            int typeId)
        {
            flushExRW.typeId(typeId);
            return this;
        }

        public KafkaMergedFlushExBuilder merged()
        {
            flushExRW.kind(KafkaApi.MERGED.value());

            return new KafkaMergedFlushExBuilder();
        }

        public KafkaFetchFlushExBuilder fetch()
        {
            flushExRW.kind(KafkaApi.FETCH.value());

            return new KafkaFetchFlushExBuilder();
        }

        public KafkaProduceFlushExBuilder produce()
        {
            flushExRW.kind(KafkaApi.PRODUCE.value());

            return new KafkaProduceFlushExBuilder();
        }

        public KafkaGroupFlushExBuilder group()
        {
            flushExRW.kind(KafkaApi.GROUP.value());

            return new KafkaGroupFlushExBuilder();
        }

        public byte[] build()
        {
            final KafkaFlushExFW flushEx = flushExRO;
            final byte[] array = new byte[flushEx.sizeof()];
            flushEx.buffer().getBytes(flushEx.offset(), array);
            return array;
        }

        public final class KafkaMergedFlushExBuilder
        {
            private final KafkaMergedFlushExFW.Builder mergedFlushExRW = new KafkaMergedFlushExFW.Builder();

            private KafkaMergedFlushExBuilder()
            {
                mergedFlushExRW.wrap(writeBuffer, KafkaFlushExFW.FIELD_OFFSET_MERGED, writeBuffer.capacity());
            }

            public KafkaMergedFlushExBuilder typeId(
                int typeId)
            {
                mergedFlushExRW.typeId(typeId);
                return this;
            }

            public KafkaMergedFetchFlushExBuilder fetchFlush()
            {
                mergedFlushExRW.kind(KafkaApi.FETCH.value());

                return new KafkaMergedFetchFlushExBuilder();
            }

            public KafkaMergedConsumerFlushExBuilder consumerFlush()
            {
                mergedFlushExRW.kind(KafkaApi.CONSUMER.value());

                return new KafkaMergedConsumerFlushExBuilder();
            }

            public KafkaFlushExBuilder build()
            {
                final KafkaMergedFlushExFW mergedFlushEx = mergedFlushExRW.build();
                flushExRO.wrap(writeBuffer, 0, mergedFlushEx.limit());
                return KafkaFlushExBuilder.this;
            }

            public final class KafkaMergedFetchFlushExBuilder
            {
                private final KafkaMergedFetchFlushExFW.Builder mergedFetchFlushExRW = new KafkaMergedFetchFlushExFW.Builder();

                private KafkaMergedFetchFlushExBuilder()
                {
                    mergedFetchFlushExRW.wrap(writeBuffer,
                        KafkaFlushExFW.FIELD_OFFSET_MERGED + KafkaMergedFlushExFW.FIELD_OFFSET_FETCH,
                        writeBuffer.capacity());
                }

                public KafkaMergedFetchFlushExBuilder progress(
                    int partitionId,
                    long offset)
                {
                    progress(partitionId, offset, DEFAULT_LATEST_OFFSET);
                    return this;
                }

                public KafkaMergedFetchFlushExBuilder progress(
                    int partitionId,
                    long offset,
                    long latestOffset)
                {
                    mergedFetchFlushExRW.progressItem(p ->
                        p.partitionId(partitionId)
                        .partitionOffset(offset)
                        .latestOffset(latestOffset));
                    return this;
                }

                public KafkaMergedFetchFlushExBuilder progress(
                    int partitionId,
                    long offset,
                    long stableOffset,
                    long latestOffset)
                {
                    mergedFetchFlushExRW.progressItem(p -> p
                        .partitionId(partitionId)
                        .partitionOffset(offset)
                        .stableOffset(stableOffset)
                        .latestOffset(latestOffset));
                    return this;
                }

                public KafkaMergedFetchFlushExBuilder capabilities(
                    String capabilities)
                {
                    mergedFetchFlushExRW.capabilities(c -> c.set(KafkaCapabilities.valueOf(capabilities)));
                    return this;
                }

                public KafkaFilterBuilder<KafkaFlushExBuilder.KafkaMergedFlushExBuilder
                    .KafkaMergedFetchFlushExBuilder> filter()
                {
                    return new KafkaFilterBuilder<>()
                    {

                        @Override
                        protected KafkaMergedFetchFlushExBuilder build(
                            KafkaFilterFW filter)
                        {
                            mergedFetchFlushExRW.filtersItem(fb -> set(fb, filter));
                            return KafkaFlushExBuilder.KafkaMergedFlushExBuilder.KafkaMergedFetchFlushExBuilder.this;
                        }
                    };
                }

                public KafkaMergedFetchFlushExBuilder partition(
                    int partitionId,
                    long partitionOffset)
                {
                    partition(partitionId, partitionOffset, DEFAULT_LATEST_OFFSET);
                    return this;
                }

                public KafkaMergedFetchFlushExBuilder partition(
                    int partitionId,
                    long partitionOffset,
                    long latestOffset)
                {
                    mergedFetchFlushExRW.partition(p -> p
                        .partitionId(partitionId)
                        .partitionOffset(partitionOffset)
                        .latestOffset(latestOffset));
                    return this;
                }


                public KafkaMergedFetchFlushExBuilder key(
                    String key)
                {
                    if (key == null)
                    {
                        mergedFetchFlushExRW.key(m -> m.length(-1)
                            .value((OctetsFW) null));
                    }
                    else
                    {
                        keyRO.wrap(key.getBytes(UTF_8));
                        mergedFetchFlushExRW.key(k -> k.length(keyRO.capacity())
                            .value(keyRO, 0, keyRO.capacity()));
                    }
                    return this;
                }

                public KafkaFlushExBuilder build()
                {
                    final KafkaMergedFetchFlushExFW mergedFetchFlushEx = mergedFetchFlushExRW.build();
                    flushExRO.wrap(writeBuffer, 0, mergedFetchFlushExRW.limit());
                    return KafkaFlushExBuilder.this;
                }
            }

            public final class KafkaMergedConsumerFlushExBuilder
            {
                private final KafkaMergedConsumerFlushExFW.Builder mergedConsumerFlushExRW =
                    new KafkaMergedConsumerFlushExFW.Builder();

                private KafkaMergedConsumerFlushExBuilder()
                {
                    mergedConsumerFlushExRW.wrap(writeBuffer, KafkaFlushExFW.FIELD_OFFSET_MERGED, writeBuffer.capacity());
                }

                public KafkaMergedConsumerFlushExBuilder partition(
                    int partitionId,
                    long partitionOffset)
                {
                    partition(partitionId, partitionOffset, DEFAULT_LATEST_OFFSET);
                    return this;
                }

                public KafkaMergedConsumerFlushExBuilder partition(
                    int partitionId,
                    long partitionOffset,
                    long latestOffset)
                {
                    mergedConsumerFlushExRW.partition(p -> p
                        .partitionId(partitionId)
                        .partitionOffset(partitionOffset)
                        .latestOffset(latestOffset));
                    return this;
                }

                public KafkaFlushExBuilder build()
                {
                    final KafkaMergedConsumerFlushExFW mergedConsumerFlushEx = mergedConsumerFlushExRW.build();
                    flushExRO.wrap(writeBuffer, 0, mergedConsumerFlushExRW.limit());
                    return KafkaFlushExBuilder.this;
                }
            }
        }

        public final class KafkaFetchFlushExBuilder
        {
            private final KafkaFetchFlushExFW.Builder fetchFlushExRW = new KafkaFetchFlushExFW.Builder();

            private KafkaFetchFlushExBuilder()
            {
                fetchFlushExRW.wrap(writeBuffer, KafkaFlushExFW.FIELD_OFFSET_FETCH, writeBuffer.capacity());
            }

            public KafkaFetchFlushExBuilder partition(
                int partitionId,
                long partitionOffset)
            {
                partition(partitionId, partitionOffset, DEFAULT_LATEST_OFFSET);
                return this;
            }

            public KafkaFetchFlushExBuilder partition(
                int partitionId,
                long partitionOffset,
                long latestOffset)
            {
                partition(partitionId, partitionOffset, latestOffset, latestOffset);
                return this;
            }

            public KafkaFetchFlushExBuilder partition(
                int partitionId,
                long offset,
                long stableOffset,
                long latestOffset)
            {
                fetchFlushExRW.partition(p -> p.partitionId(partitionId)
                    .partitionOffset(offset)
                    .stableOffset(stableOffset)
                    .latestOffset(latestOffset));
                return this;
            }

            public KafkaFetchFlushExBuilder transaction(
                String result,
                long producerId)
            {
                fetchFlushExRW.transactionsItem(t -> t
                    .result(r -> r.set(KafkaTransactionResult.valueOf(result)))
                    .producerId(producerId));
                return this;
            }

            public KafkaFilterBuilder<KafkaFetchFlushExBuilder> filter()
            {
                return new KafkaFilterBuilder<>()
                {
                    @Override
                    protected KafkaFetchFlushExBuilder build(
                        KafkaFilterFW filter)
                    {
                        fetchFlushExRW.filtersItem(fb -> set(fb, filter));
                        return KafkaFetchFlushExBuilder.this;
                    }
                };
            }

            public KafkaFlushExBuilder build()
            {
                final KafkaFetchFlushExFW fetchFlushEx = fetchFlushExRW.build();
                flushExRO.wrap(writeBuffer, 0, fetchFlushEx.limit());
                return KafkaFlushExBuilder.this;
            }
        }

        public final class KafkaProduceFlushExBuilder
        {
            private final KafkaProduceFlushExFW.Builder produceFlushExRW = new KafkaProduceFlushExFW.Builder();

            private KafkaProduceFlushExBuilder()
            {
                produceFlushExRW.wrap(writeBuffer, KafkaFlushExFW.FIELD_OFFSET_FETCH, writeBuffer.capacity());
            }

            public KafkaProduceFlushExBuilder partition(
                int partitionId,
                long partitionOffset)
            {
                partition(partitionId, partitionOffset, DEFAULT_LATEST_OFFSET);
                return this;
            }

            public KafkaProduceFlushExBuilder partition(
                int partitionId,
                long partitionOffset,
                long latestOffset)
            {
                produceFlushExRW.partition(p -> p
                    .partitionId(partitionId)
                    .partitionOffset(partitionOffset)
                    .latestOffset(latestOffset));
                return this;
            }


            public KafkaProduceFlushExBuilder key(
                String key)
            {
                if (key == null)
                {
                    produceFlushExRW.key(m -> m.length(-1)
                        .value((OctetsFW) null));
                }
                else
                {
                    keyRO.wrap(key.getBytes(UTF_8));
                    produceFlushExRW.key(k -> k.length(keyRO.capacity())
                        .value(keyRO, 0, keyRO.capacity()));
                }
                return this;
            }

            public KafkaFlushExBuilder build()
            {
                final KafkaProduceFlushExFW produceFlushEx = produceFlushExRW.build();
                flushExRO.wrap(writeBuffer, 0, produceFlushEx.limit());
                return KafkaFlushExBuilder.this;
            }
        }

        public final class KafkaGroupFlushExBuilder
        {
            private final KafkaGroupFlushExFW.Builder flushDataExRW = new KafkaGroupFlushExFW.Builder();

            private KafkaGroupFlushExBuilder()
            {
                flushDataExRW.wrap(writeBuffer, KafkaFlushExFW.FIELD_OFFSET_GROUP, writeBuffer.capacity());
            }

            public KafkaGroupFlushExBuilder leaderId(
                String leaderId)
            {
                flushDataExRW.leaderId(leaderId);
                return this;
            }

            public KafkaGroupFlushExBuilder memberId(
                String memberId)
            {
                flushDataExRW.memberId(memberId);
                return this;
            }

            public KafkaGroupFlushExBuilder members(
                String memberId,
                byte[] metadata)
            {
                flushDataExRW.members(m -> m.item(gm -> gm.id(memberId).metadata(md -> md.put(metadata))));
                return this;
            }

            public KafkaFlushExBuilder build()
            {
                final KafkaGroupFlushExFW groupDataEx = flushDataExRW.build();
                flushExRO.wrap(writeBuffer, 0, groupDataEx.limit());
                return KafkaFlushExBuilder.this;
            }
        }
    }

    public static final class KafkaResetExBuilder
    {
        private final MutableDirectBuffer writeBuffer = new UnsafeBuffer(new byte[1024 * 8]);

        private final KafkaResetExFW resetExRO = new KafkaResetExFW();

        private final KafkaResetExFW.Builder resetExRW = new KafkaResetExFW.Builder();

        private KafkaResetExBuilder()
        {
            resetExRW.wrap(writeBuffer, 0, writeBuffer.capacity());
        }

        public KafkaResetExBuilder typeId(
            int typeId)
        {
            resetExRW.typeId(typeId);
            return this;
        }

        public KafkaResetExBuilder error(
            int error)
        {
            resetExRW.error(error);
            return this;
        }

        public byte[] build()
        {
            final KafkaResetExFW resetEx = resetExRW.build();
            final byte[] array = new byte[resetEx.sizeof()];
            resetEx.buffer().getBytes(resetExRO.offset(), array);
            return array;
        }
    }

    public static final class KafkaDataExMatcherBuilder
    {
        private final DirectBuffer bufferRO = new UnsafeBuffer();

        private final DirectBuffer keyRO = new UnsafeBuffer(0, 0);
        private final DirectBuffer hashKeyRO = new UnsafeBuffer(0, 0);
        private final DirectBuffer nameRO = new UnsafeBuffer(0, 0);
        private final DirectBuffer valueRO = new UnsafeBuffer(0, 0);

        private final KafkaDataExFW dataExRO = new KafkaDataExFW();

        private Integer typeId;
        private Integer kind;
        private Predicate<KafkaDataExFW> caseMatcher;

        public KafkaMergedDataExMatcherBuilder merged()
        {
            final KafkaMergedDataExMatcherBuilder matcherBuilder = new KafkaMergedDataExMatcherBuilder();

            this.kind = KafkaApi.MERGED.value();
            this.caseMatcher = matcherBuilder::match;
            return matcherBuilder;
        }

        public KafkaFetchDataExMatcherBuilder fetch()
        {
            final KafkaFetchDataExMatcherBuilder matcherBuilder = new KafkaFetchDataExMatcherBuilder();

            this.kind = KafkaApi.FETCH.value();
            this.caseMatcher = matcherBuilder::match;
            return matcherBuilder;
        }

        public KafkaProduceDataExMatcherBuilder produce()
        {
            final KafkaProduceDataExMatcherBuilder matcherBuilder = new KafkaProduceDataExMatcherBuilder();

            this.kind = KafkaApi.PRODUCE.value();
            this.caseMatcher = matcherBuilder::match;
            return matcherBuilder;
        }

        public KafkaDataExMatcherBuilder typeId(
            int typeId)
        {
            this.typeId = typeId;
            return this;
        }

        public BytesMatcher build()
        {
            return typeId != null || kind != null ? this::match : buf -> null;
        }

        private KafkaDataExFW match(
            ByteBuffer byteBuf) throws Exception
        {
            if (!byteBuf.hasRemaining())
            {
                return null;
            }

            bufferRO.wrap(byteBuf);
            final KafkaDataExFW dataEx = dataExRO.tryWrap(bufferRO, byteBuf.position(), byteBuf.capacity());

            if (dataEx != null &&
                matchTypeId(dataEx) &&
                matchKind(dataEx) &&
                matchCase(dataEx))
            {
                byteBuf.position(byteBuf.position() + dataEx.sizeof());
                return dataEx;
            }

            throw new Exception(dataEx.toString());
        }

        private boolean matchTypeId(
            final KafkaDataExFW dataEx)
        {
            return typeId == null || typeId == dataEx.typeId();
        }

        private boolean matchKind(
            final KafkaDataExFW dataEx)
        {
            return kind == null || kind == dataEx.kind();
        }

        private boolean matchCase(
            final KafkaDataExFW dataEx) throws Exception
        {
            return caseMatcher == null || caseMatcher.test(dataEx);
        }

        public final class KafkaFetchDataExMatcherBuilder
        {
            private Integer deferred;
            private Long timestamp;
            private Long filters;
            private Long producerId;
            private KafkaOffsetFW.Builder partitionRW;
            private KafkaKeyFW.Builder keyRW;
            private KafkaDeltaFW.Builder deltaRW;
            private Array32FW.Builder<KafkaHeaderFW.Builder, KafkaHeaderFW> headersRW;

            private KafkaFetchDataExMatcherBuilder()
            {
            }

            public KafkaFetchDataExMatcherBuilder deferred(
                int deferred)
            {
                this.deferred = deferred;
                return this;
            }

            public KafkaFetchDataExMatcherBuilder timestamp(
                long timestamp)
            {
                this.timestamp = timestamp;
                return this;
            }

            public KafkaFetchDataExMatcherBuilder filters(
                    long filters)
            {
                this.filters = filters;
                return this;
            }

            public KafkaFetchDataExMatcherBuilder producerId(
                long producerId)
            {
                this.producerId = producerId;
                return this;
            }

            public KafkaFetchDataExMatcherBuilder partition(
                int partitionId,
                long partitionOffset)
            {
                partition(partitionId, partitionOffset, DEFAULT_LATEST_OFFSET);
                return this;
            }

            public KafkaFetchDataExMatcherBuilder partition(
                int partitionId,
                long partitionOffset,
                long latestOffset)
            {
                partition(partitionId, partitionOffset, latestOffset, latestOffset);
                return this;
            }

            public KafkaFetchDataExMatcherBuilder partition(
                int partitionId,
                long partitionOffset,
                long stableOffset,
                long latestOffset)
            {
                assert partitionRW == null;
                partitionRW = new KafkaOffsetFW.Builder().wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);

                partitionRW
                    .partitionId(partitionId)
                    .partitionOffset(partitionOffset)
                    .stableOffset(stableOffset)
                    .latestOffset(latestOffset);

                return this;
            }

            public KafkaFetchDataExMatcherBuilder key(
                String key)
            {
                assert keyRW == null;
                keyRW = new KafkaKeyFW.Builder().wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);

                if (key == null)
                {
                    keyRW.length(-1)
                         .value((OctetsFW) null);
                }
                else
                {
                    keyRO.wrap(key.getBytes(UTF_8));
                    keyRW.length(keyRO.capacity())
                         .value(keyRO, 0, keyRO.capacity());
                }

                return this;
            }

            public KafkaFetchDataExMatcherBuilder delta(
                String delta,
                long ancestorOffset)
            {
                assert deltaRW == null;
                deltaRW = new KafkaDeltaFW.Builder().wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);

                deltaRW.type(t -> t.set(KafkaDeltaType.valueOf(delta))).ancestorOffset(ancestorOffset);

                return this;
            }

            public KafkaFetchDataExMatcherBuilder header(
                String name,
                String value)
            {
                if (headersRW == null)
                {
                    this.headersRW = new Array32FW.Builder<>(new KafkaHeaderFW.Builder(), new KafkaHeaderFW())
                                                .wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);
                }

                if (value == null)
                {
                    nameRO.wrap(name.getBytes(UTF_8));
                    headersRW.item(i -> i.nameLen(nameRO.capacity())
                                         .name(nameRO, 0, nameRO.capacity())
                                         .valueLen(-1)
                                         .value((OctetsFW) null));
                }
                else
                {
                    nameRO.wrap(name.getBytes(UTF_8));
                    valueRO.wrap(value.getBytes(UTF_8));
                    headersRW.item(i -> i.nameLen(nameRO.capacity())
                                         .name(nameRO, 0, nameRO.capacity())
                                         .valueLen(valueRO.capacity())
                                         .value(valueRO, 0, valueRO.capacity()));
                }

                return this;
            }

            public KafkaDataExMatcherBuilder build()
            {
                return KafkaDataExMatcherBuilder.this;
            }

            private boolean match(
                KafkaDataExFW dataEx)
            {
                final KafkaFetchDataExFW fetchDataEx = dataEx.fetch();
                return matchDeferred(fetchDataEx) &&
                    matchTimestamp(fetchDataEx) &&
                    matchProducerId(fetchDataEx) &&
                    matchPartition(fetchDataEx) &&
                    matchKey(fetchDataEx) &&
                    matchDelta(fetchDataEx) &&
                    matchHeaders(fetchDataEx) &&
                    matchFilters(fetchDataEx);
            }

            private boolean matchDeferred(
                final KafkaFetchDataExFW fetchDataEx)
            {
                return deferred == null || deferred == fetchDataEx.deferred();
            }

            private boolean matchTimestamp(
                final KafkaFetchDataExFW fetchDataEx)
            {
                return timestamp == null || timestamp == fetchDataEx.timestamp();
            }

            private boolean matchProducerId(
                final KafkaFetchDataExFW fetchDataEx)
            {
                return producerId == null || producerId == fetchDataEx.producerId();
            }

            private boolean matchPartition(
                final KafkaFetchDataExFW fetchDataEx)
            {
                return partitionRW == null || partitionRW.build().equals(fetchDataEx.partition());
            }

            private boolean matchKey(
                    final KafkaFetchDataExFW fetchDataEx)
            {
                return keyRW == null || keyRW.build().equals(fetchDataEx.key());
            }

            private boolean matchDelta(
                final KafkaFetchDataExFW fetchDataEx)
            {
                return deltaRW == null || deltaRW.build().equals(fetchDataEx.delta());
            }

            private boolean matchFilters(
                final KafkaFetchDataExFW fetchDataEx)
            {
                return filters == null || filters == fetchDataEx.filters();
            }

            private boolean matchHeaders(
                final KafkaFetchDataExFW fetchDataEx)
            {
                return headersRW == null || headersRW.build().equals(fetchDataEx.headers());
            }
        }

        public final class KafkaProduceDataExMatcherBuilder
        {
            private Integer deferred;
            private Long timestamp;
            private Integer sequence;
            private KafkaAckMode ackMode;
            private KafkaKeyFW.Builder keyRW;
            private Array32FW.Builder<KafkaHeaderFW.Builder, KafkaHeaderFW> headersRW;

            private KafkaProduceDataExMatcherBuilder()
            {
            }

            public KafkaProduceDataExMatcherBuilder deferred(
                int deferred)
            {
                this.deferred = deferred;
                return this;
            }

            public KafkaProduceDataExMatcherBuilder timestamp(
                long timestamp)
            {
                this.timestamp = timestamp;
                return this;
            }

            public KafkaProduceDataExMatcherBuilder sequence(
                int sequence)
            {
                this.sequence = sequence;
                return this;
            }

            public KafkaProduceDataExMatcherBuilder ackMode(
                String ackMode)
            {
                assert this.ackMode == null;
                this.ackMode = KafkaAckMode.valueOf(ackMode);
                return this;
            }

            public KafkaProduceDataExMatcherBuilder key(
                String key)
            {
                assert keyRW == null;
                keyRW = new KafkaKeyFW.Builder().wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);

                if (key == null)
                {
                    keyRW.length(-1)
                         .value((OctetsFW) null);
                }
                else
                {
                    keyRO.wrap(key.getBytes(UTF_8));
                    keyRW.length(keyRO.capacity())
                         .value(keyRO, 0, keyRO.capacity());
                }

                return this;
            }

            public KafkaProduceDataExMatcherBuilder header(
                String name,
                String value)
            {
                if (headersRW == null)
                {
                    this.headersRW = new Array32FW.Builder<>(new KafkaHeaderFW.Builder(), new KafkaHeaderFW())
                                                  .wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);
                }

                if (value == null)
                {
                    nameRO.wrap(name.getBytes(UTF_8));
                    headersRW.item(i -> i.nameLen(nameRO.capacity())
                                         .name(nameRO, 0, nameRO.capacity())
                                         .valueLen(-1)
                                         .value((OctetsFW) null));
                }
                else
                {
                    nameRO.wrap(name.getBytes(UTF_8));
                    valueRO.wrap(value.getBytes(UTF_8));
                    headersRW.item(i -> i.nameLen(nameRO.capacity())
                                         .name(nameRO, 0, nameRO.capacity())
                                         .valueLen(valueRO.capacity())
                                         .value(valueRO, 0, valueRO.capacity()));
                }

                return this;
            }

            public KafkaDataExMatcherBuilder build()
            {
                return KafkaDataExMatcherBuilder.this;
            }

            private boolean match(
                KafkaDataExFW dataEx)
            {
                final KafkaProduceDataExFW produceDataEx = dataEx.produce();
                return matchDeferred(produceDataEx) &&
                    matchTimestamp(produceDataEx) &&
                    matchSequence(produceDataEx) &&
                    matchAckMode(produceDataEx) &&
                    matchKey(produceDataEx) &&
                    matchHeaders(produceDataEx);
            }

            private boolean matchDeferred(
                final KafkaProduceDataExFW produceDataEx)
            {
                return deferred == null || deferred == produceDataEx.deferred();
            }

            private boolean matchTimestamp(
                final KafkaProduceDataExFW produceDataEx)
            {
                return timestamp == null || timestamp == produceDataEx.timestamp();
            }

            private boolean matchSequence(
                final KafkaProduceDataExFW produceDataEx)
            {
                return sequence == null || sequence == produceDataEx.sequence();
            }

            private boolean matchAckMode(
                final KafkaProduceDataExFW produceDataEx)
            {
                return ackMode == null || ackMode == produceDataEx.ackMode().get();
            }

            private boolean matchKey(
                final KafkaProduceDataExFW produceDataEx)
            {
                return keyRW == null || keyRW.build().equals(produceDataEx.key());
            }

            private boolean matchHeaders(
                final KafkaProduceDataExFW produceDataEx)
            {
                return headersRW == null || headersRW.build().equals(produceDataEx.headers());
            }
        }

        public final class KafkaMergedDataExMatcherBuilder
        {
            private Integer deferred;
            private Long timestamp;
            private Long filters;
            private KafkaOffsetFW.Builder partitionRW;
            private Array32FW.Builder<KafkaOffsetFW.Builder, KafkaOffsetFW> progressRW;
            private KafkaDeltaFW.Builder deltaRW;
            private KafkaKeyFW.Builder keyRW;
            private KafkaKeyFW.Builder hashKeyRW;
            private Array32FW.Builder<KafkaHeaderFW.Builder, KafkaHeaderFW> headersRW;

            private KafkaMergedDataExMatcherBuilder()
            {
            }

            public KafkaMergedDataExMatcherBuilder deferred(
                int deferred)
            {
                this.deferred = deferred;
                return this;
            }

            public KafkaMergedDataExMatcherBuilder timestamp(
                long timestamp)
            {
                this.timestamp = timestamp;
                return this;
            }

            public KafkaMergedDataExMatcherBuilder filters(
                long filters)
            {
                this.filters = filters;
                return this;
            }

            public KafkaMergedDataExMatcherBuilder partition(
                int partitionId,
                long offset)
            {
                partition(partitionId, offset, DEFAULT_LATEST_OFFSET);
                return this;
            }

            public KafkaMergedDataExMatcherBuilder partition(
                int partitionId,
                long offset,
                long latestOffset)
            {
                assert partitionRW == null;
                partitionRW = new KafkaOffsetFW.Builder().wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);

                partitionRW.partitionId(partitionId).partitionOffset(offset).latestOffset(latestOffset);

                return this;
            }

            public KafkaMergedDataExMatcherBuilder progress(
                int partitionId,
                long offset)
            {
                progress(partitionId, offset, DEFAULT_LATEST_OFFSET);
                return this;
            }

            public KafkaMergedDataExMatcherBuilder progress(
                int partitionId,
                long offset,
                long latestOffset)
            {
                if (progressRW == null)
                {
                    this.progressRW = new Array32FW.Builder<>(new KafkaOffsetFW.Builder(), new KafkaOffsetFW())
                                                 .wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);
                }
                progressRW.item(i -> i.partitionId(partitionId).partitionOffset(offset).latestOffset(latestOffset));
                return this;
            }

            public KafkaMergedDataExMatcherBuilder key(
                String key)
            {
                assert keyRW == null;
                keyRW = new KafkaKeyFW.Builder().wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);

                if (key == null)
                {
                    keyRW.length(-1)
                         .value((OctetsFW) null);
                }
                else
                {
                    keyRO.wrap(key.getBytes(UTF_8));
                    keyRW.length(keyRO.capacity())
                         .value(keyRO, 0, keyRO.capacity());
                }

                return this;
            }

            public KafkaMergedDataExMatcherBuilder hashKey(
                String hashKey)
            {
                assert hashKeyRW == null;
                hashKeyRW = new KafkaKeyFW.Builder().wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);

                if (hashKey == null)
                {
                    hashKeyRW.length(-1)
                        .value((OctetsFW) null);
                }
                else
                {
                    hashKeyRO.wrap(hashKey.getBytes(UTF_8));
                    hashKeyRW.length(hashKeyRO.capacity())
                        .value(hashKeyRO, 0, hashKeyRO.capacity());
                }

                return this;
            }

            public KafkaMergedDataExMatcherBuilder delta(
                String delta,
                long ancestorOffset)
            {
                assert deltaRW == null;
                deltaRW = new KafkaDeltaFW.Builder().wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);

                deltaRW.type(t -> t.set(KafkaDeltaType.valueOf(delta))).ancestorOffset(ancestorOffset);

                return this;
            }

            public KafkaMergedDataExMatcherBuilder header(
                String name,
                String value)
            {
                if (headersRW == null)
                {
                    this.headersRW = new Array32FW.Builder<>(new KafkaHeaderFW.Builder(), new KafkaHeaderFW())
                                                .wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);
                }

                if (value == null)
                {
                    nameRO.wrap(name.getBytes(UTF_8));
                    headersRW.item(i -> i.nameLen(nameRO.capacity())
                                         .name(nameRO, 0, nameRO.capacity())
                                         .valueLen(-1)
                                         .value((OctetsFW) null));
                }
                else
                {
                    nameRO.wrap(name.getBytes(UTF_8));
                    valueRO.wrap(value.getBytes(UTF_8));
                    headersRW.item(i -> i.nameLen(nameRO.capacity())
                                         .name(nameRO, 0, nameRO.capacity())
                                         .valueLen(valueRO.capacity())
                                         .value(valueRO, 0, valueRO.capacity()));
                }

                return this;
            }

            public KafkaMergedDataExMatcherBuilder headerNull(
                String name)
            {
                if (headersRW == null)
                {
                    this.headersRW = new Array32FW.Builder<>(new KafkaHeaderFW.Builder(), new KafkaHeaderFW())
                                                .wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);
                }
                nameRO.wrap(name.getBytes(UTF_8));
                headersRW.item(i -> i.nameLen(nameRO.capacity())
                                     .name(nameRO, 0, nameRO.capacity())
                                     .valueLen(-1)
                                     .value((OctetsFW) null));
                return this;
            }

            public KafkaMergedDataExMatcherBuilder headerByte(
                String name,
                byte value)
            {
                if (headersRW == null)
                {
                    this.headersRW = new Array32FW.Builder<>(new KafkaHeaderFW.Builder(), new KafkaHeaderFW())
                                                .wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);
                }
                nameRO.wrap(name.getBytes(UTF_8));
                valueRO.wrap(ByteBuffer.allocate(Byte.BYTES).put(value));
                headersRW.item(i -> i.nameLen(nameRO.capacity())
                                     .name(nameRO, 0, nameRO.capacity())
                                     .valueLen(valueRO.capacity())
                                     .value(valueRO, 0, valueRO.capacity()));
                return this;
            }

            public KafkaMergedDataExMatcherBuilder headerShort(
                String name,
                short value)
            {
                if (headersRW == null)
                {
                    this.headersRW = new Array32FW.Builder<>(new KafkaHeaderFW.Builder(), new KafkaHeaderFW())
                                                .wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);
                }
                nameRO.wrap(name.getBytes(UTF_8));
                valueRO.wrap(ByteBuffer.allocate(Short.BYTES).putShort(value));
                headersRW.item(i -> i.nameLen(nameRO.capacity())
                                     .name(nameRO, 0, nameRO.capacity())
                                     .valueLen(valueRO.capacity())
                                     .value(valueRO, 0, valueRO.capacity()));
                return this;
            }

            public KafkaMergedDataExMatcherBuilder headerInt(
                String name,
                int value)
            {
                if (headersRW == null)
                {
                    this.headersRW = new Array32FW.Builder<>(new KafkaHeaderFW.Builder(), new KafkaHeaderFW())
                                                .wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);
                }
                nameRO.wrap(name.getBytes(UTF_8));
                valueRO.wrap(ByteBuffer.allocate(Integer.BYTES).putInt(value));
                headersRW.item(i -> i.nameLen(nameRO.capacity())
                                     .name(nameRO, 0, nameRO.capacity())
                                     .valueLen(valueRO.capacity())
                                     .value(valueRO, 0, valueRO.capacity()));
                return this;
            }

            public KafkaMergedDataExMatcherBuilder headerLong(
                String name,
                long value)
            {
                if (headersRW == null)
                {
                    this.headersRW = new Array32FW.Builder<>(new KafkaHeaderFW.Builder(), new KafkaHeaderFW())
                                                .wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);
                }
                nameRO.wrap(name.getBytes(UTF_8));
                valueRO.wrap(ByteBuffer.allocate(Long.BYTES).putLong(value));
                headersRW.item(i -> i.nameLen(nameRO.capacity())
                                     .name(nameRO, 0, nameRO.capacity())
                                     .valueLen(valueRO.capacity())
                                     .value(valueRO, 0, valueRO.capacity()));
                return this;
            }

            public KafkaMergedDataExMatcherBuilder headerBytes(
                String name,
                byte[] value)
            {
                if (headersRW == null)
                {
                    this.headersRW = new Array32FW.Builder<>(new KafkaHeaderFW.Builder(), new KafkaHeaderFW())
                                                .wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);
                }

                if (value == null)
                {
                    nameRO.wrap(name.getBytes(UTF_8));
                    headersRW.item(i -> i.nameLen(nameRO.capacity())
                                         .name(nameRO, 0, nameRO.capacity())
                                         .valueLen(-1)
                                         .value((OctetsFW) null));
                }
                else
                {
                    nameRO.wrap(name.getBytes(UTF_8));
                    valueRO.wrap(value);
                    headersRW.item(i -> i.nameLen(nameRO.capacity())
                                         .name(nameRO, 0, nameRO.capacity())
                                         .valueLen(valueRO.capacity())
                                         .value(valueRO, 0, valueRO.capacity()));
                }

                return this;
            }

            public KafkaDataExMatcherBuilder build()
            {
                return KafkaDataExMatcherBuilder.this;
            }

            private boolean match(
                KafkaDataExFW dataEx)
            {
                final KafkaMergedDataExFW mergedDataEx = dataEx.merged();
                return matchPartition(mergedDataEx) &&
                    matchProgress(mergedDataEx) &&
                    matchDeferred(mergedDataEx) &&
                    matchTimestamp(mergedDataEx) &&
                    matchKey(mergedDataEx) &&
                    matchHashKey(mergedDataEx) &&
                    matchDelta(mergedDataEx) &&
                    matchHeaders(mergedDataEx) &&
                    matchFilters(mergedDataEx);
            }

            private boolean matchPartition(
                final KafkaMergedDataExFW mergedDataEx)
            {
                return partitionRW == null || partitionRW.build().equals(mergedDataEx.partition());
            }

            private boolean matchProgress(
                final KafkaMergedDataExFW mergedDataEx)
            {
                return progressRW == null || progressRW.build().equals(mergedDataEx.progress());
            }

            private boolean matchDeferred(
                final KafkaMergedDataExFW mergedDataEx)
            {
                return deferred == null || deferred == mergedDataEx.deferred();
            }

            private boolean matchTimestamp(
                final KafkaMergedDataExFW mergedDataEx)
            {
                return timestamp == null || timestamp == mergedDataEx.timestamp();
            }

            private boolean matchKey(
                final KafkaMergedDataExFW mergedDataEx)
            {
                return keyRW == null || keyRW.build().equals(mergedDataEx.key());
            }

            private boolean matchHashKey(
                final KafkaMergedDataExFW mergedDataEx)
            {
                return hashKeyRW == null || hashKeyRW.build().equals(mergedDataEx.hashKey());
            }

            private boolean matchDelta(
                final KafkaMergedDataExFW mergedDataEx)
            {
                return deltaRW == null || deltaRW.build().equals(mergedDataEx.delta());
            }

            private boolean matchHeaders(
                final KafkaMergedDataExFW mergedDataEx)
            {
                return headersRW == null || headersRW.build().equals(mergedDataEx.headers());
            }

            private boolean matchFilters(
                final KafkaMergedDataExFW mergedDataEx)
            {
                return filters == null || filters == mergedDataEx.filters();
            }
        }
    }

    public static final class KafkaFlushExMatcherBuilder
    {
        private final DirectBuffer bufferRO = new UnsafeBuffer();

        private final KafkaFlushExFW flushExRO = new KafkaFlushExFW();
        private final DirectBuffer keyRO = new UnsafeBuffer(0, 0);

        private Integer typeId;
        private Integer kind;
        private Predicate<KafkaFlushExFW> caseMatcher;

        public KafkaMergedFlushExMatcherBuilder merged()
        {
            final KafkaMergedFlushExMatcherBuilder matcherBuilder = new KafkaMergedFlushExMatcherBuilder();

            this.kind = KafkaApi.MERGED.value();
            this.caseMatcher = matcherBuilder::match;
            return matcherBuilder;
        }

        public KafkaFetchFlushExMatcherBuilder fetch()
        {
            final KafkaFetchFlushExMatcherBuilder matcherBuilder = new KafkaFetchFlushExMatcherBuilder();

            this.kind = KafkaApi.FETCH.value();
            this.caseMatcher = matcherBuilder::match;
            return matcherBuilder;
        }

        public KafkaProduceFlushExMatcherBuilder produce()
        {
            final KafkaProduceFlushExMatcherBuilder matcherBuilder = new KafkaProduceFlushExMatcherBuilder();

            this.kind = KafkaApi.PRODUCE.value();
            this.caseMatcher = matcherBuilder::match;
            return matcherBuilder;
        }

        public KafkaGroupFlushExMatchBuilder group()
        {
            final KafkaGroupFlushExMatchBuilder matcherBuilder = new KafkaGroupFlushExMatchBuilder();

            this.kind = KafkaApi.GROUP.value();
            this.caseMatcher = matcherBuilder::match;
            return matcherBuilder;
        }

        public KafkaFlushExMatcherBuilder typeId(
            int typeId)
        {
            this.typeId = typeId;
            return this;
        }

        public BytesMatcher build()
        {
            return typeId != null || kind != null ? this::match : buf -> null;
        }

        private KafkaFlushExFW match(
            ByteBuffer byteBuf) throws Exception
        {
            if (!byteBuf.hasRemaining())
            {
                return null;
            }

            bufferRO.wrap(byteBuf);
            final KafkaFlushExFW flushEx = flushExRO.tryWrap(bufferRO, byteBuf.position(), byteBuf.capacity());

            if (flushEx != null &&
                matchTypeId(flushEx) &&
                matchKind(flushEx) &&
                matchCase(flushEx))
            {
                byteBuf.position(byteBuf.position() + flushEx.sizeof());
                return flushEx;
            }

            throw new Exception(flushEx.toString());
        }

        private boolean matchTypeId(
            final KafkaFlushExFW flushEx)
        {
            return typeId == null || typeId == flushEx.typeId();
        }

        private boolean matchKind(
            final KafkaFlushExFW flushEx)
        {
            return kind == null || kind == flushEx.kind();
        }

        private boolean matchCase(
            final KafkaFlushExFW flushEx) throws Exception
        {
            return caseMatcher == null || caseMatcher.test(flushEx);
        }

        public final class KafkaFetchFlushExMatcherBuilder
        {
            private KafkaOffsetFW.Builder partitionRW;
            private Array32FW.Builder<KafkaTransactionFW.Builder, KafkaTransactionFW> transactionRW;

            private Array32FW.Builder<KafkaFilterFW.Builder, KafkaFilterFW> filtersRW;

            private KafkaFetchFlushExMatcherBuilder()
            {
            }

            public KafkaFetchFlushExMatcherBuilder partition(
                int partitionId,
                long partitionOffset)
            {
                partition(partitionId, partitionOffset, DEFAULT_LATEST_OFFSET);
                return this;
            }

            public KafkaFetchFlushExMatcherBuilder partition(
                int partitionId,
                long partitionOffset,
                long latestOffset)
            {
                partition(partitionId, partitionOffset, latestOffset, latestOffset);
                return this;
            }

            public KafkaFetchFlushExMatcherBuilder partition(
                int partitionId,
                long partitionOffset,
                long stableOffset,
                long latestOffset)
            {
                assert partitionRW == null;
                partitionRW = new KafkaOffsetFW.Builder().wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);

                partitionRW
                        .partitionId(partitionId)
                        .partitionOffset(partitionOffset)
                        .stableOffset(stableOffset)
                        .latestOffset(latestOffset);

                return this;
            }

            public KafkaFetchFlushExMatcherBuilder transaction(
                String result,
                long producerId)
            {
                assert transactionRW == null;
                transactionRW = new Array32FW.Builder<>(new KafkaTransactionFW.Builder(), new KafkaTransactionFW())
                        .wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);

                transactionRW.item(t -> t
                        .result(r -> r.set(KafkaTransactionResult.valueOf(result)))
                        .producerId(producerId));
                return this;
            }

            public KafkaFilterBuilder<KafkaFlushExMatcherBuilder.KafkaFetchFlushExMatcherBuilder> filter()
            {
                if (filtersRW == null)
                {
                    filtersRW = new Array32FW.Builder<>(new KafkaFilterFW.Builder(), new KafkaFilterFW())
                        .wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);
                }

                return new KafkaFilterBuilder<>()
                {
                    @Override
                    protected KafkaFlushExMatcherBuilder.KafkaFetchFlushExMatcherBuilder build(
                        KafkaFilterFW filter)
                    {
                        filtersRW.item(fb -> set(fb, filter));
                        return KafkaFlushExMatcherBuilder.KafkaFetchFlushExMatcherBuilder.this;
                    }
                };
            }

            public KafkaFlushExMatcherBuilder build()
            {
                return KafkaFlushExMatcherBuilder.this;
            }

            private boolean match(
                KafkaFlushExFW flushEx)
            {
                final KafkaFetchFlushExFW fetchFlushEx = flushEx.fetch();
                return matchPartition(fetchFlushEx) &&
                       matchTransaction(fetchFlushEx) &&
                       matchFilters(fetchFlushEx);
            }

            private boolean matchPartition(
                final KafkaFetchFlushExFW fetchFlushEx)
            {
                return partitionRW == null || partitionRW.build().equals(fetchFlushEx.partition());
            }

            private boolean matchTransaction(
                final KafkaFetchFlushExFW fetchFlushEx)
            {
                return transactionRW == null || transactionRW.build().equals(fetchFlushEx.transactions());
            }

            private boolean matchFilters(
                final KafkaFetchFlushExFW fetchFlushEx)
            {
                return filtersRW == null || filtersRW.build().equals(fetchFlushEx.filters());
            }
        }

        public final class KafkaMergedFlushExMatcherBuilder
        {
            KafkaMergedFetchFlushEx mergedFetchFlush;

            private KafkaMergedFlushExMatcherBuilder()
            {
            }

            public boolean match(
                KafkaFlushExFW kafkaFlushEx)
            {
                boolean matched = false;
                if (kafkaFlushEx.merged().typeId() == KafkaApi.FETCH.value())
                {
                    matched = mergedFetch().match(kafkaFlushEx);
                }
                return matched;
            }

            public KafkaMergedFetchFlushEx mergedFetch()
            {
                if (mergedFetchFlush == null)
                {
                    mergedFetchFlush = new KafkaMergedFetchFlushEx();
                }
                return  mergedFetchFlush;
            }

            public final class KafkaMergedFetchFlushEx
            {
                private Array32FW.Builder<KafkaOffsetFW.Builder, KafkaOffsetFW> progressRW;
                private KafkaKeyFW.Builder keyRW;
                private KafkaOffsetFW.Builder partitionRW;

                private Array32FW.Builder<KafkaFilterFW.Builder, KafkaFilterFW> filtersRW;

                private KafkaMergedFetchFlushEx()
                {
                }

                public KafkaMergedFetchFlushEx progress(
                    int partitionId,
                    long offset)
                {
                    progress(partitionId, offset, DEFAULT_LATEST_OFFSET);
                    return this;
                }

                public KafkaMergedFetchFlushEx progress(
                    int partitionId,
                    long offset,
                    long latestOffset)
                {
                    if (progressRW == null)
                    {
                        this.progressRW = new Array32FW.Builder<>(new KafkaOffsetFW.Builder(), new KafkaOffsetFW())
                            .wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);
                    }
                    progressRW.item(i -> i.partitionId(partitionId).partitionOffset(offset).latestOffset(latestOffset));
                    return this;
                }

                public KafkaMergedFetchFlushEx progress(
                    int partitionId,
                    long offset,
                    long stableOffset,
                    long latestOffset)
                {
                    if (progressRW == null)
                    {
                        this.progressRW = new Array32FW.Builder<>(new KafkaOffsetFW.Builder(), new KafkaOffsetFW())
                            .wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);
                    }
                    progressRW.item(i -> i
                        .partitionId(partitionId)
                        .partitionOffset(offset)
                        .stableOffset(stableOffset)
                        .latestOffset(latestOffset));
                    return this;
                }

                public KafkaMergedFetchFlushEx partition(
                    int partitionId,
                    long offset)
                {
                    partition(partitionId, offset, DEFAULT_LATEST_OFFSET);
                    return this;
                }

                public KafkaMergedFetchFlushEx partition(
                    int partitionId,
                    long offset,
                    long latestOffset)
                {
                    assert partitionRW == null;
                    partitionRW = new KafkaOffsetFW.Builder().wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);

                    partitionRW.partitionId(partitionId).partitionOffset(offset).latestOffset(latestOffset);

                    return this;
                }

                public KafkaMergedFetchFlushEx key(
                    String key)
                {
                    assert keyRW == null;
                    keyRW = new KafkaKeyFW.Builder().wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);

                    if (key == null)
                    {
                        keyRW.length(-1)
                            .value((OctetsFW) null);
                    }
                    else
                    {
                        keyRO.wrap(key.getBytes(UTF_8));
                        keyRW.length(keyRO.capacity())
                            .value(keyRO, 0, keyRO.capacity());
                    }

                    return this;
                }

                public KafkaFilterBuilder
                    <KafkaFlushExMatcherBuilder.KafkaMergedFlushExMatcherBuilder.KafkaMergedFetchFlushEx>
                    filter()
                {
                    if (filtersRW == null)
                    {
                        filtersRW = new Array32FW.Builder<>(new KafkaFilterFW.Builder(), new KafkaFilterFW())
                            .wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);
                    }

                    return new KafkaFilterBuilder<>()
                    {
                        @Override
                        protected KafkaFlushExMatcherBuilder.KafkaMergedFlushExMatcherBuilder.KafkaMergedFetchFlushEx
                            build(
                            KafkaFilterFW filter)
                        {
                            filtersRW.item(fb -> set(fb, filter));
                            return KafkaMergedFetchFlushEx.this;
                        }
                    };
                }

                public KafkaFlushExMatcherBuilder build()
                {
                    return KafkaFlushExMatcherBuilder.this;
                }

                private boolean match(
                    KafkaFlushExFW flushEx)
                {
                    final KafkaMergedFetchFlushExFW fetch = flushEx.merged().fetch();
                    return matchProgress(fetch) &&
                        matchKey(fetch) &&
                        matchPartition(fetch) &&
                        matchFilters(fetch);
                }

                private boolean matchProgress(
                    final KafkaMergedFetchFlushExFW mergedFlush)
                {
                    return progressRW == null || progressRW.build().equals(mergedFlush.progress());
                }

                private boolean matchPartition(
                    final KafkaMergedFetchFlushExFW mergedFlush)
                {
                    return partitionRW == null || partitionRW.build().equals(mergedFlush.partition());
                }

                private boolean matchKey(
                    final KafkaMergedFetchFlushExFW mergedFlush)
                {
                    return keyRW == null || keyRW.build().equals(mergedFlush.key());
                }

                private boolean matchFilters(
                    final KafkaMergedFetchFlushExFW mergedFlush)
                {
                    return filtersRW == null || filtersRW.build().equals(mergedFlush.filters());
                }
            }
        }

        public final class KafkaProduceFlushExMatcherBuilder
        {
            private KafkaKeyFW.Builder keyRW;
            private KafkaOffsetFW.Builder partitionRW;

            private KafkaProduceFlushExMatcherBuilder()
            {
            }

            public KafkaProduceFlushExMatcherBuilder partition(
                int partitionId,
                long offset)
            {
                partition(partitionId, offset, DEFAULT_LATEST_OFFSET);
                return this;
            }

            public KafkaProduceFlushExMatcherBuilder partition(
                int partitionId,
                long offset,
                long latestOffset)
            {
                assert partitionRW == null;
                partitionRW = new KafkaOffsetFW.Builder().wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);

                partitionRW.partitionId(partitionId).partitionOffset(offset).latestOffset(latestOffset);

                return this;
            }

            public KafkaProduceFlushExMatcherBuilder key(
                String key)
            {
                assert keyRW == null;
                keyRW = new KafkaKeyFW.Builder().wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);

                if (key == null)
                {
                    keyRW.length(-1)
                        .value((OctetsFW) null);
                }
                else
                {
                    keyRO.wrap(key.getBytes(UTF_8));
                    keyRW.length(keyRO.capacity())
                        .value(keyRO, 0, keyRO.capacity());
                }

                return this;
            }

            public KafkaFlushExMatcherBuilder build()
            {
                return KafkaFlushExMatcherBuilder.this;
            }

            private boolean match(
                KafkaFlushExFW flushEx)
            {
                final KafkaProduceFlushExFW produceFlushEx = flushEx.produce();
                return matchKey(produceFlushEx) &&
                    matchPartition(produceFlushEx);
            }

            private boolean matchPartition(
                final KafkaProduceFlushExFW produceFlushEx)
            {
                return partitionRW == null || partitionRW.build().equals(produceFlushEx.partition());
            }

            private boolean matchKey(
                final KafkaProduceFlushExFW produceFlushEx)
            {
                return keyRW == null || keyRW.build().equals(produceFlushEx.key());
            }
        }

        public final class KafkaGroupFlushExMatchBuilder
        {
            private String16FW leaderId;
            private String16FW memberId;
            private Array32FW.Builder<KafkaGroupMemberFW.Builder, KafkaGroupMemberFW> membersRW;

            private KafkaGroupFlushExMatchBuilder()
            {
            }

            public KafkaGroupFlushExMatchBuilder leaderId(
                String leaderId)
            {
                this.leaderId = new String16FW(leaderId);
                return this;
            }

            public KafkaGroupFlushExMatchBuilder memberId(
                String memberId)
            {
                this.memberId = new String16FW(memberId);
                return this;
            }

            public KafkaGroupFlushExMatchBuilder members(
                String memberId,
                byte[] metadata)
            {
                if (membersRW == null)
                {
                    this.membersRW = new Array32FW.Builder<>(new KafkaGroupMemberFW.Builder(), new KafkaGroupMemberFW())
                        .wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);
                }
                this.membersRW.item(m -> m.id(memberId).metadata(md -> md.put(metadata)));
                return this;
            }

            public KafkaGroupFlushExMatchBuilder build()
            {
                return KafkaGroupFlushExMatchBuilder.this;
            }

            private boolean match(
                KafkaFlushExFW flushEx)
            {
                final KafkaGroupFlushExFW groupFlushEx = flushEx.group();
                return matchLeaderId(groupFlushEx) &&
                    matchMemberId(groupFlushEx) &&
                    matchMembers(groupFlushEx);
            }

            private boolean matchLeaderId(
                final KafkaGroupFlushExFW groupFLushEx)
            {
                return leaderId == null || leaderId.equals(groupFLushEx.leaderId());
            }

            private boolean matchMemberId(
                final KafkaGroupFlushExFW groupFLushEx)
            {
                return memberId == null || memberId.equals(groupFLushEx.memberId());
            }

            private boolean matchMembers(
                final KafkaGroupFlushExFW groupFLushEx)
            {
                return membersRW == null || membersRW.build().equals(groupFLushEx.members());
            }
        }
    }

    public static final class KafkaBeginExMatcherBuilder
    {
        private final DirectBuffer bufferRO = new UnsafeBuffer();

        private final KafkaBeginExFW beginExRO = new KafkaBeginExFW();

        private Integer typeId;
        private Integer kind;
        private Predicate<KafkaBeginExFW> caseMatcher;

        public KafkaMergedBeginExMatcherBuilder merged()
        {
            final KafkaMergedBeginExMatcherBuilder matcherBuilder = new KafkaMergedBeginExMatcherBuilder();

            this.kind = KafkaApi.MERGED.value();
            this.caseMatcher = matcherBuilder::match;
            return matcherBuilder;
        }

        public KafkaFetchBeginExMatcherBuilder fetch()
        {
            final KafkaFetchBeginExMatcherBuilder matcherBuilder = new KafkaFetchBeginExMatcherBuilder();

            this.kind = KafkaApi.FETCH.value();
            this.caseMatcher = matcherBuilder::match;
            return matcherBuilder;
        }

        public KafkaProduceBeginExMatcherBuilder produce()
        {
            final KafkaProduceBeginExMatcherBuilder matcherBuilder = new KafkaProduceBeginExMatcherBuilder();

            this.kind = KafkaApi.PRODUCE.value();
            this.caseMatcher = matcherBuilder::match;
            return matcherBuilder;
        }

        public KafkaGroupBeginExMatcherBuilder group()
        {
            final KafkaGroupBeginExMatcherBuilder matcherBuilder = new KafkaGroupBeginExMatcherBuilder();

            this.kind = KafkaApi.GROUP.value();
            this.caseMatcher = matcherBuilder::match;
            return matcherBuilder;
        }

        public KafkaBeginExMatcherBuilder typeId(
            int typeId)
        {
            this.typeId = typeId;
            return this;
        }

        public BytesMatcher build()
        {
            return typeId != null || kind != null ? this::match : buf -> null;
        }

        private KafkaBeginExFW match(
            ByteBuffer byteBuf) throws Exception
        {
            if (!byteBuf.hasRemaining())
            {
                return null;
            }

            bufferRO.wrap(byteBuf);
            final KafkaBeginExFW beginEx = beginExRO.tryWrap(bufferRO, byteBuf.position(), byteBuf.capacity());

            if (beginEx != null &&
                matchTypeId(beginEx) &&
                matchKind(beginEx) &&
                matchCase(beginEx))
            {
                byteBuf.position(byteBuf.position() + beginEx.sizeof());
                return beginEx;
            }

            throw new Exception(beginEx.toString());
        }

        private boolean matchTypeId(
            final KafkaBeginExFW beginEx)
        {
            return typeId == null || typeId == beginEx.typeId();
        }

        private boolean matchKind(
            final KafkaBeginExFW beginEx)
        {
            return kind == null || kind == beginEx.kind();
        }

        private boolean matchCase(
            final KafkaBeginExFW beginEx) throws Exception
        {
            return caseMatcher == null || caseMatcher.test(beginEx);
        }

        public final class KafkaFetchBeginExMatcherBuilder
        {
            private String16FW topic;
            private KafkaDeltaTypeFW deltaType;
            private KafkaIsolationFW isolation;
            private KafkaEvaluationFW evaluation;

            private KafkaOffsetFW.Builder partitionRW;
            private Array32FW.Builder<KafkaFilterFW.Builder, KafkaFilterFW> filtersRW;

            private KafkaFetchBeginExMatcherBuilder()
            {
            }

            public KafkaFetchBeginExMatcherBuilder topic(
                String topic)
            {
                this.topic = new String16FW(topic);
                return this;
            }

            public KafkaFetchBeginExMatcherBuilder partition(
                int partitionId,
                long partitionOffset)
            {
                partition(partitionId, partitionOffset, DEFAULT_LATEST_OFFSET);
                return this;
            }

            public KafkaFetchBeginExMatcherBuilder partition(
                int partitionId,
                long partitionOffset,
                long latestOffset)
            {
                assert partitionRW == null;
                partitionRW = new KafkaOffsetFW.Builder().wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);

                partitionRW.partitionId(partitionId).partitionOffset(partitionOffset).latestOffset(latestOffset);

                return this;
            }

            public KafkaFetchBeginExMatcherBuilder partition(
                    int partitionId,
                    long partitionOffset,
                    long stableOffset,
                    long latestOffset)
            {
                assert partitionRW == null;
                partitionRW = new KafkaOffsetFW.Builder().wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);

                partitionRW
                    .partitionId(partitionId)
                    .partitionOffset(partitionOffset)
                    .stableOffset(stableOffset)
                    .latestOffset(latestOffset);

                return this;
            }

            public KafkaFilterBuilder<KafkaFetchBeginExMatcherBuilder> filter()
            {
                if (filtersRW == null)
                {
                    filtersRW = new Array32FW.Builder<>(new KafkaFilterFW.Builder(), new KafkaFilterFW())
                            .wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);
                }

                return new KafkaFilterBuilder<>()
                {

                    @Override
                    protected KafkaFetchBeginExMatcherBuilder build(
                        KafkaFilterFW filter)
                    {
                        filtersRW.item(fb -> set(fb, filter));
                        return KafkaFetchBeginExMatcherBuilder.this;
                    }
                };
            }

            public KafkaFetchBeginExMatcherBuilder isolation(
                String isolation)
            {
                assert this.isolation == null;
                this.isolation = new KafkaIsolationFW.Builder().wrap(new UnsafeBuffer(new byte[64]), 0, 64)
                        .set(KafkaIsolation.valueOf(isolation))
                        .build();
                return this;
            }

            public KafkaFetchBeginExMatcherBuilder deltaType(
                String deltaType)
            {
                assert this.deltaType == null;
                this.deltaType = new KafkaDeltaTypeFW.Builder().wrap(new UnsafeBuffer(new byte[64]), 0, 64)
                        .set(KafkaDeltaType.valueOf(deltaType))
                        .build();
                return this;
            }

            public KafkaFetchBeginExMatcherBuilder evaluation(
                String evaluation)
            {
                assert this.evaluation == null;
                this.evaluation = new KafkaEvaluationFW.Builder().wrap(new UnsafeBuffer(new byte[64]), 0, 64)
                        .set(KafkaEvaluation.valueOf(evaluation))
                        .build();
                return this;
            }

            public KafkaBeginExMatcherBuilder build()
            {
                return KafkaBeginExMatcherBuilder.this;
            }

            private boolean match(
                KafkaBeginExFW beginEx)
            {
                final KafkaFetchBeginExFW fetchBeginEx = beginEx.fetch();
                return matchTopic(fetchBeginEx) &&
                    matchPartition(fetchBeginEx) &&
                    matchFilters(fetchBeginEx) &&
                    matchIsolation(fetchBeginEx) &&
                    matchDeltaType(fetchBeginEx) &&
                    matchEvaluation(fetchBeginEx);
            }

            private boolean matchTopic(
                final KafkaFetchBeginExFW fetchBeginEx)
            {
                return topic == null || topic.equals(fetchBeginEx.topic());
            }

            private boolean matchPartition(
                final KafkaFetchBeginExFW fetchBeginEx)
            {
                return partitionRW == null || partitionRW.build().equals(fetchBeginEx.partition());
            }

            private boolean matchFilters(
                final KafkaFetchBeginExFW fetchBeginEx)
            {
                return filtersRW == null || filtersRW.build().equals(fetchBeginEx.filters());
            }

            private boolean matchIsolation(
                final KafkaFetchBeginExFW fetchBeginEx)
            {
                return isolation == null || isolation.equals(fetchBeginEx.isolation());
            }

            private boolean matchDeltaType(
                final KafkaFetchBeginExFW fetchBeginEx)
            {
                return deltaType == null || deltaType.equals(fetchBeginEx.deltaType());
            }

            private boolean matchEvaluation(
                final KafkaFetchBeginExFW fetchBeginEx)
            {
                return evaluation == null || evaluation.equals(fetchBeginEx.evaluation());
            }
        }

        public final class KafkaProduceBeginExMatcherBuilder
        {
            private String8FW transaction;
            private Long producerId;
            private String16FW topic;
            private KafkaOffsetFW.Builder partitionRW;

            private KafkaProduceBeginExMatcherBuilder()
            {
            }

            public KafkaProduceBeginExMatcherBuilder transaction(
                String transaction)
            {
                this.transaction = new String8FW(transaction);
                return this;
            }

            public KafkaProduceBeginExMatcherBuilder producerId(
                long producerId)
            {
                this.producerId = producerId;
                return this;
            }

            public KafkaProduceBeginExMatcherBuilder topic(
                String topic)
            {
                this.topic = new String16FW(topic);
                return this;
            }

            public KafkaProduceBeginExMatcherBuilder partition(
                int partitionId)
            {
                partition(partitionId, DEFAULT_LATEST_OFFSET, DEFAULT_LATEST_OFFSET);
                return this;
            }

            public KafkaProduceBeginExMatcherBuilder partition(
                int partitionId,
                long offset)
            {
                partition(partitionId, offset, DEFAULT_LATEST_OFFSET);
                return this;
            }

            public KafkaProduceBeginExMatcherBuilder partition(
                int partitionId,
                long offset,
                long latestOffset)
            {
                assert partitionRW == null;
                partitionRW = new KafkaOffsetFW.Builder().wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);

                partitionRW.partitionId(partitionId).partitionOffset(offset).latestOffset(latestOffset);

                return this;
            }

            public KafkaBeginExMatcherBuilder build()
            {
                return KafkaBeginExMatcherBuilder.this;
            }

            private boolean match(
                KafkaBeginExFW beginEx)
            {
                final KafkaProduceBeginExFW produceBeginEx = beginEx.produce();
                return matchTransaction(produceBeginEx) &&
                    matchProducerId(produceBeginEx) &&
                    matchTopic(produceBeginEx) &&
                    matchPartition(produceBeginEx);
            }

            private boolean matchTransaction(
                final KafkaProduceBeginExFW produceBeginEx)
            {
                return transaction == null || transaction.equals(produceBeginEx.transaction());
            }

            private boolean matchProducerId(
                final KafkaProduceBeginExFW produceBeginEx)
            {
                return producerId == null || producerId == produceBeginEx.producerId();
            }

            private boolean matchTopic(
                final KafkaProduceBeginExFW produceBeginEx)
            {
                return topic == null || topic.equals(produceBeginEx.topic());
            }

            private boolean matchPartition(
                final KafkaProduceBeginExFW produceBeginEx)
            {
                return partitionRW == null || partitionRW.build().equals(produceBeginEx.partition());
            }

        }

        public final class KafkaGroupBeginExMatcherBuilder
        {
            private String16FW groupId;
            private String16FW protocol;
            private int timeout;

            private KafkaGroupMemberMetadataFW.Builder metadataRW;

            private KafkaGroupBeginExMatcherBuilder()
            {
            }

            public KafkaGroupBeginExMatcherBuilder groupId(
                String groupId)
            {
                this.groupId = new String16FW(groupId);
                return this;
            }

            public KafkaGroupBeginExMatcherBuilder protocol(
                String protocol)
            {
                this.protocol = new String16FW(protocol);
                return this;
            }

            public KafkaGroupBeginExMatcherBuilder timeout(
                int timeout)
            {
                this.timeout = timeout;
                return this;
            }

            public KafkaGroupBeginExMatcherBuilder metadata(
                String consumerId,
                String topic,
                int partitionId)
            {
                metadataRW = new KafkaGroupMemberMetadataFW.Builder().wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);
                metadataRW
                    .consumerId(consumerId)
                    .topics(t -> t.item(tp -> tp.topic(topic)
                        .partitions(p -> p.item(pi -> pi.partitionId(partitionId)))));
                return this;
            }

            public KafkaBeginExMatcherBuilder build()
            {
                return KafkaBeginExMatcherBuilder.this;
            }

            private boolean match(
                KafkaBeginExFW beginEx)
            {
                final KafkaGroupBeginExFW groupBeginEx = beginEx.group();
                return matchGroupId(groupBeginEx) &&
                    matchGroupId(groupBeginEx) &&
                    matchProtocol(groupBeginEx) &&
                    matchTimeout(groupBeginEx) &&
                    matchMetadata(groupBeginEx);
            }

            private boolean matchGroupId(
                final KafkaGroupBeginExFW groupBeginExFW)
            {
                return groupId == null || groupId.equals(groupBeginExFW.groupId());
            }

            private boolean matchProtocol(
                final KafkaGroupBeginExFW groupBeginExFW)
            {
                return protocol == null || protocol.equals(groupBeginExFW.protocol());
            }

            private boolean matchTimeout(
                final KafkaGroupBeginExFW groupBeginExFW)
            {
                return timeout == 0 || timeout == groupBeginExFW.timeout();
            }

            private boolean matchMetadata(
                final KafkaGroupBeginExFW groupBeginExFW)
            {
                KafkaGroupMemberMetadataFW metadata = groupBeginExFW.metadata();
                return metadataRW == null ||
                    metadataRW.build().consumerId().equals(metadata.consumerId()) &&
                    metadataRW.build().topics().fieldCount() == groupBeginExFW.metadata().topics().fieldCount();
            }
        }

        public final class KafkaMergedBeginExMatcherBuilder
        {
            private KafkaCapabilities capabilities;
            private String16FW topic;
            private Array32FW.Builder<KafkaOffsetFW.Builder, KafkaOffsetFW> partitionsRW;
            private KafkaIsolation isolation;
            private KafkaDeltaType deltaType;
            private KafkaEvaluation evaluation;
            private KafkaAckMode ackMode;
            private Array32FW.Builder<KafkaFilterFW.Builder, KafkaFilterFW> filtersRW;

            private KafkaMergedBeginExMatcherBuilder()
            {
            }

            public KafkaMergedBeginExMatcherBuilder capabilities(
                String capabilities)
            {
                assert this.capabilities == null;
                this.capabilities = KafkaCapabilities.valueOf(capabilities);
                return this;
            }

            public KafkaMergedBeginExMatcherBuilder topic(
                String topic)
            {
                this.topic = new String16FW(topic);
                return this;
            }

            public KafkaMergedBeginExMatcherBuilder partition(
                int partitionId,
                long offset)
            {
                partition(partitionId, offset, DEFAULT_LATEST_OFFSET);
                return this;
            }

            public KafkaMergedBeginExMatcherBuilder partition(
                int partitionId,
                long offset,
                long latestOffset)
            {
                partition(partitionId, offset, latestOffset, latestOffset);
                return this;
            }

            public KafkaMergedBeginExMatcherBuilder partition(
                int partitionId,
                long offset,
                long stableOffset,
                long latestOffset)
            {
                if (partitionsRW == null)
                {
                    this.partitionsRW = new Array32FW.Builder<>(new KafkaOffsetFW.Builder(), new KafkaOffsetFW())
                                                 .wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);
                }
                partitionsRW.item(i -> i
                    .partitionId(partitionId)
                    .partitionOffset(offset)
                    .stableOffset(stableOffset)
                    .latestOffset(latestOffset));
                return this;
            }

            public KafkaFilterBuilder<KafkaMergedBeginExMatcherBuilder> filter()
            {
                if (filtersRW == null)
                {
                    filtersRW = new Array32FW.Builder<>(new KafkaFilterFW.Builder(), new KafkaFilterFW())
                            .wrap(new UnsafeBuffer(new byte[1024]), 0, 1024);
                }

                return new KafkaFilterBuilder<>()
                {

                    @Override
                    protected KafkaMergedBeginExMatcherBuilder build(
                        KafkaFilterFW filter)
                    {
                        filtersRW.item(fb -> set(fb, filter));
                        return KafkaMergedBeginExMatcherBuilder.this;
                    }
                };
            }

            public KafkaMergedBeginExMatcherBuilder isolation(
                String isolation)
            {
                assert this.isolation == null;
                this.isolation = KafkaIsolation.valueOf(isolation);
                return this;
            }

            public KafkaMergedBeginExMatcherBuilder deltaType(
                String deltaType)
            {
                assert this.deltaType == null;
                this.deltaType = KafkaDeltaType.valueOf(deltaType);
                return this;
            }

            public KafkaMergedBeginExMatcherBuilder evaluation(
                String evaluation)
            {
                assert this.evaluation == null;
                this.evaluation = KafkaEvaluation.valueOf(evaluation);
                return this;
            }

            public KafkaMergedBeginExMatcherBuilder ackMode(
                String ackMode)
            {
                assert this.ackMode == null;
                this.ackMode = KafkaAckMode.valueOf(ackMode);
                return this;
            }

            public KafkaBeginExMatcherBuilder build()
            {
                return KafkaBeginExMatcherBuilder.this;
            }

            private boolean match(
                KafkaBeginExFW beginEx)
            {
                final KafkaMergedBeginExFW mergedBeginEx = beginEx.merged();
                return matchCapabilities(mergedBeginEx) &&
                    matchTopic(mergedBeginEx) &&
                    matchPartitions(mergedBeginEx) &&
                    matchFilters(mergedBeginEx) &&
                    matchIsolation(mergedBeginEx) &&
                    matchDeltaType(mergedBeginEx) &&
                    matchAckMode(mergedBeginEx) &&
                    matchEvaluation(mergedBeginEx);
            }

            private boolean matchCapabilities(
                final KafkaMergedBeginExFW mergedBeginEx)
            {
                return capabilities == null || capabilities == mergedBeginEx.capabilities().get();
            }

            private boolean matchTopic(
                final KafkaMergedBeginExFW mergedBeginEx)
            {
                return topic == null || topic.equals(mergedBeginEx.topic());
            }

            private boolean matchPartitions(
                final KafkaMergedBeginExFW mergedBeginEx)
            {
                return partitionsRW == null || partitionsRW.build().equals(mergedBeginEx.partitions());
            }

            private boolean matchFilters(
                final KafkaMergedBeginExFW mergedBeginEx)
            {
                return filtersRW == null || filtersRW.build().equals(mergedBeginEx.filters());
            }

            private boolean matchEvaluation(
                final KafkaMergedBeginExFW mergedBeginEx)
            {
                return evaluation == null || evaluation == (mergedBeginEx.evaluation().get());
            }

            private boolean matchIsolation(
                final KafkaMergedBeginExFW mergedBeginEx)
            {
                return isolation == null || isolation == mergedBeginEx.isolation().get();
            }

            private boolean matchDeltaType(
                final KafkaMergedBeginExFW mergedBeginEx)
            {
                return deltaType == null || deltaType == mergedBeginEx.deltaType().get();
            }

            private boolean matchAckMode(
                final KafkaMergedBeginExFW mergedBeginEx)
            {
                return ackMode == null || ackMode == mergedBeginEx.ackMode().get();
            }
        }
    }

    @Function
    public static long offset(
        String type)
    {
        return KafkaOffsetType.valueOf(type).value();
    }

    private KafkaFunctions()
    {
        // utility
    }

    public static class Mapper extends FunctionMapperSpi.Reflective
    {

        public Mapper()
        {
            super(KafkaFunctions.class);
        }

        @Override
        public String getPrefixName()
        {
            return "kafka";
        }
    }
}
