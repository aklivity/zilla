/*
 * Copyright 2021-2022 Aklivity Inc.
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

import static io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaCapabilities.FETCH_ONLY;
import static io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaCapabilities.PRODUCE_ONLY;
import static io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaOffsetType.HISTORICAL;
import static io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaOffsetType.LIVE;
import static io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.WindowFW.Builder.DEFAULT_MINIMUM;
import static io.aklivity.zilla.runtime.engine.budget.BudgetCreditor.NO_CREDITOR_INDEX;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.LongFunction;
import java.util.function.LongUnaryOperator;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Int2IntHashMap;
import org.agrona.collections.Long2LongHashMap;
import org.agrona.collections.MutableInteger;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.kafka.internal.KafkaBinding;
import io.aklivity.zilla.runtime.binding.kafka.internal.KafkaConfiguration;
import io.aklivity.zilla.runtime.binding.kafka.internal.budget.MergedBudgetCreditor;
import io.aklivity.zilla.runtime.binding.kafka.internal.config.KafkaBindingConfig;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.ArrayFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.Flyweight;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaAckMode;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaCapabilities;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaConditionFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaConfigFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaDeltaFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaDeltaType;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaEvaluationFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaFilterFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaHeaderFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaHeadersFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaIsolation;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaKeyFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaNotFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaOffsetFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaOffsetType;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaPartitionFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.KafkaValueMatchFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.ExtensionFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaBeginExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaDataExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaDescribeDataExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaFetchDataExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaFetchFlushExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaFlushExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaMergedBeginExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaMergedDataExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaMergedFlushExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaMetaDataExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaResetExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.WindowFW;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;

public final class KafkaMergedFactory implements BindingHandler
{
    private static final String16FW CONFIG_NAME_CLEANUP_POLICY = new String16FW("cleanup.policy");
    private static final String16FW CONFIG_NAME_MAX_MESSAGE_BYTES = new String16FW("max.message.bytes");
    private static final String16FW CONFIG_NAME_SEGMENT_BYTES = new String16FW("segment.bytes");
    private static final String16FW CONFIG_NAME_SEGMENT_INDEX_BYTES = new String16FW("segment.index.bytes");
    private static final String16FW CONFIG_NAME_SEGMENT_MILLIS = new String16FW("segment.ms");
    private static final String16FW CONFIG_NAME_RETENTION_BYTES = new String16FW("retention.bytes");
    private static final String16FW CONFIG_NAME_RETENTION_MILLIS = new String16FW("retention.ms");
    private static final String16FW CONFIG_NAME_DELETE_RETENTION_MILLIS = new String16FW("delete.retention.ms");
    private static final String16FW CONFIG_NAME_MIN_COMPACTION_LAG_MILLIS = new String16FW("min.compaction.lag.ms");
    private static final String16FW CONFIG_NAME_MAX_COMPACTION_LAG_MILLIS = new String16FW("max.compaction.lag.ms");
    private static final String16FW CONFIG_NAME_MIN_CLEANABLE_DIRTY_RATIO = new String16FW("min.cleanable.dirty.ratio");

    private static final int ERROR_NOT_LEADER_FOR_PARTITION = 6;
    private static final int ERROR_UNKNOWN = -1;

    private static final int FLAGS_NONE = 0x00;
    private static final int FLAGS_FIN = 0x01;
    private static final int FLAGS_INIT = 0x02;
    private static final int FLAGS_INCOMPLETE = 0x04;
    private static final int FLAGS_INIT_AND_FIN = FLAGS_INIT | FLAGS_FIN;

    private static final int DYNAMIC_PARTITION = -1;

    private static final DirectBuffer EMPTY_BUFFER = new UnsafeBuffer();
    private static final OctetsFW EMPTY_OCTETS = new OctetsFW().wrap(EMPTY_BUFFER, 0, 0);
    private static final Consumer<OctetsFW.Builder> EMPTY_EXTENSION = ex -> {};
    private static final MessageConsumer NO_RECEIVER = (m, b, i, l) -> {};

    private static final List<KafkaMergedFilter> EMPTY_MERGED_FILTERS = Collections.emptyList();

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final EndFW endRO = new EndFW();
    private final AbortFW abortRO = new AbortFW();
    private final FlushFW flushRO = new FlushFW();
    private final ResetFW resetRO = new ResetFW();
    private final WindowFW windowRO = new WindowFW();
    private final ExtensionFW extensionRO = new ExtensionFW();
    private final KafkaBeginExFW kafkaBeginExRO = new KafkaBeginExFW();
    private final KafkaDataExFW kafkaDataExRO = new KafkaDataExFW();
    private final KafkaFlushExFW kafkaFlushExRO = new KafkaFlushExFW();
    private final KafkaResetExFW kafkaResetExRO = new KafkaResetExFW();

    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final EndFW.Builder endRW = new EndFW.Builder();
    private final FlushFW.Builder flushRW = new FlushFW.Builder();
    private final AbortFW.Builder abortRW = new AbortFW.Builder();
    private final ResetFW.Builder resetRW = new ResetFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final KafkaBeginExFW.Builder kafkaBeginExRW = new KafkaBeginExFW.Builder();
    private final KafkaDataExFW.Builder kafkaDataExRW = new KafkaDataExFW.Builder();
    private final KafkaFlushExFW.Builder kafkaFlushExRW = new KafkaFlushExFW.Builder();

    private final MutableInteger partitionCount = new MutableInteger();
    private final MutableInteger initialNoAckRW = new MutableInteger();
    private final MutableInteger initialPadRW = new MutableInteger();
    private final MutableInteger initialMaxRW = new MutableInteger();

    private final int kafkaTypeId;
    private final MutableDirectBuffer writeBuffer;
    private final MutableDirectBuffer extBuffer;
    private final LongUnaryOperator supplyInitialId;
    private final LongUnaryOperator supplyReplyId;
    private final LongConsumer detachSender;
    private final BindingHandler streamFactory;
    private final LongFunction<KafkaBindingConfig> supplyBinding;
    private final MergedBudgetCreditor creditor;

    public KafkaMergedFactory(
        KafkaConfiguration config,
        EngineContext context,
        LongFunction<KafkaBindingConfig> supplyBinding,
        MergedBudgetCreditor creditor)
    {
        this.kafkaTypeId = context.supplyTypeId(KafkaBinding.NAME);
        this.writeBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.extBuffer = new UnsafeBuffer(new byte[context.writeBuffer().capacity()]);
        this.supplyInitialId = context::supplyInitialId;
        this.supplyReplyId = context::supplyReplyId;
        this.detachSender = context::detachSender;
        this.streamFactory = context.streamFactory();
        this.supplyBinding = supplyBinding;
        this.creditor = creditor;
    }

    @Override
    public MessageConsumer newStream(
        int msgTypeId,
        DirectBuffer buffer,
        int index,
        int length,
        MessageConsumer sender)
    {
        final BeginFW begin = beginRO.wrap(buffer, index, index + length);
        final long originId = begin.originId();
        final long routedId = begin.routedId();
        final long initialId = begin.streamId();
        final long authorization = begin.authorization();
        final long affinity = begin.affinity();

        assert (initialId & 0x0000_0000_0000_0001L) != 0L;

        final OctetsFW extension = begin.extension();
        final ExtensionFW beginEx = extensionRO.tryWrap(extension.buffer(), extension.offset(), extension.limit());
        final KafkaBeginExFW kafkaBeginEx = beginEx != null && beginEx.typeId() == kafkaTypeId ?
                kafkaBeginExRO.tryWrap(extension.buffer(), extension.offset(), extension.limit()) : null;

        assert kafkaBeginEx != null;
        assert kafkaBeginEx.kind() == KafkaBeginExFW.KIND_MERGED;
        final KafkaMergedBeginExFW kafkaMergedBeginEx = kafkaBeginEx.merged();
        final KafkaCapabilities capabilities = kafkaMergedBeginEx.capabilities().get();
        final String16FW beginTopic = kafkaMergedBeginEx.topic();
        final String topicName = beginTopic.asString();
        final KafkaIsolation isolation = kafkaMergedBeginEx.isolation().get();
        final KafkaDeltaType deltaType = kafkaMergedBeginEx.deltaType().get();
        final KafkaAckMode ackMode = kafkaMergedBeginEx.ackMode().get();

        MessageConsumer newStream = null;

        final KafkaBindingConfig binding = supplyBinding.apply(routedId);

        if (binding != null)
        {
            final long resolvedId = routedId;
            final ArrayFW<KafkaOffsetFW> partitions = kafkaMergedBeginEx.partitions();

            final KafkaOffsetFW partition = partitions.matchFirst(p -> p.partitionId() == -1L);
            final long defaultOffset = partition != null ? partition.partitionOffset() : HISTORICAL.value();

            final Long2LongHashMap initialOffsetsById = new Long2LongHashMap(-3L);
            partitions.forEach(p ->
            {
                final long partitionId = p.partitionId();
                if (partitionId >= 0L)
                {
                    final long partitionOffset = p.partitionOffset();
                    initialOffsetsById.put(partitionId, partitionOffset);
                }
            });

            newStream = new KafkaMergedStream(
                    sender,
                    originId,
                    routedId,
                    initialId,
                    affinity,
                    authorization,
                    topicName,
                    resolvedId,
                    capabilities,
                    initialOffsetsById,
                    defaultOffset,
                    isolation,
                    deltaType,
                    ackMode)::onMergedMessage;
        }

        return newStream;
    }

    private static List<KafkaMergedFilter> asMergedFilters(
        ArrayFW<KafkaFilterFW> filters)
    {
        final List<KafkaMergedFilter> mergedFilters;

        if (filters.isEmpty())
        {
            mergedFilters = EMPTY_MERGED_FILTERS;
        }
        else
        {
            mergedFilters = new ArrayList<>();
            filters.forEach(f -> mergedFilters.add(asMergedFilter(f.conditions())));
        }

        return mergedFilters;
    }

    private static KafkaMergedFilter asMergedFilter(
        ArrayFW<KafkaConditionFW> conditions)
    {
        assert !conditions.isEmpty();

        final List<KafkaMergedCondition> mergedConditions = new ArrayList<>();
        conditions.forEach(c -> mergedConditions.add(asMergedCondition(c)));
        return new KafkaMergedFilter(mergedConditions);
    }

    private static KafkaMergedCondition asMergedCondition(
        KafkaConditionFW condition)
    {
        KafkaMergedCondition mergedCondition = null;

        switch (condition.kind())
        {
        case KafkaConditionFW.KIND_KEY:
            mergedCondition = asMergedCondition(condition.key());
            break;
        case KafkaConditionFW.KIND_HEADER:
            mergedCondition = asMergedCondition(condition.header());
            break;
        case KafkaConditionFW.KIND_NOT:
            mergedCondition = asMergedCondition(condition.not());
            break;
        case KafkaConditionFW.KIND_HEADERS:
            mergedCondition = asMergedCondition(condition.headers());
            break;
        }

        return mergedCondition;
    }

    private static KafkaMergedCondition asMergedCondition(
        KafkaKeyFW key)
    {
        final OctetsFW value = key.value();

        DirectBuffer valueBuffer = null;
        if (value != null)
        {
            valueBuffer = copyBuffer(value);
        }

        return new KafkaMergedCondition.Key(valueBuffer);
    }

    private static KafkaMergedCondition asMergedCondition(
        KafkaHeaderFW header)
    {
        final OctetsFW name = header.name();
        final OctetsFW value = header.value();

        DirectBuffer nameBuffer = null;
        if (name != null)
        {
            nameBuffer = copyBuffer(name);
        }

        DirectBuffer valueBuffer = null;
        if (value != null)
        {
            valueBuffer = copyBuffer(value);
        }

        return new KafkaMergedCondition.Header(nameBuffer, valueBuffer);
    }

    private static KafkaMergedCondition asMergedCondition(
        KafkaHeadersFW headers)
    {
        final OctetsFW name = headers.name();
        final Array32FW<KafkaValueMatchFW> values = headers.values();

        DirectBuffer nameBuffer = null;
        if (name != null)
        {
            nameBuffer = copyBuffer(name);
        }

        DirectBuffer valuesBuffer = null;
        if (values != null)
        {
            valuesBuffer = copyBuffer(values);
        }

        return new KafkaMergedCondition.Headers(nameBuffer, valuesBuffer);
    }

    private static KafkaMergedCondition asMergedCondition(
        KafkaNotFW not)
    {
        return new KafkaMergedCondition.Not(asMergedCondition(not.condition()));
    }

    private static DirectBuffer copyBuffer(
        Flyweight value)
    {
        final DirectBuffer buffer = value.buffer();
        final int index = value.offset();
        final int length = value.sizeof();
        final MutableDirectBuffer copy = new UnsafeBuffer(new byte[length]);
        copy.putBytes(0, buffer, index, length);
        return copy;
    }

    private static int defaultKeyHash(
        KafkaKeyFW key)
    {
        final OctetsFW value = key.value();
        final DirectBuffer buffer = value.buffer();
        final int offset = value.offset();
        final int limit = value.limit();

        final int length = limit - offset;
        final int seed = 0x9747b28c;

        final int m = 0x5bd1e995;
        final int r = 24;

        int h = seed ^ length;
        int length4 = length >> 2;
        for (int i = 0; i < length4; i++)
        {
            final int i4 = offset + i * 4;
            int k = (buffer.getByte(i4 + 0) & 0xff) +
                    ((buffer.getByte(i4 + 1) & 0xff) << 8) +
                    ((buffer.getByte(i4 + 2) & 0xff) << 16) +
                    ((buffer.getByte(i4 + 3) & 0xff) << 24);
            k *= m;
            k ^= k >>> r;
            k *= m;
            h *= m;
            h ^= k;
        }

        final int remaining = length - 4 * length4;
        if (remaining == 3)
        {
            h ^= (buffer.getByte(offset + (length & ~3) + 2) & 0xff) << 16;
        }
        if (remaining >= 2)
        {
            h ^= (buffer.getByte(offset + (length & ~3) + 1) & 0xff) << 8;
        }
        if (remaining >= 1)
        {
            h ^= buffer.getByte(offset + (length & ~3)) & 0xff;
            h *= m;
        }

        h ^= h >>> 13;
        h *= m;
        h ^= h >>> 15;
        return h;
    }

    private static final class KafkaMergedFilter
    {
        private final List<KafkaMergedCondition> conditions;

        private KafkaMergedFilter(
            List<KafkaMergedCondition> conditions)
        {
            this.conditions = conditions;
        }

        @Override
        public int hashCode()
        {
            return conditions.hashCode();
        }

        @Override
        public boolean equals(
            Object obj)
        {
            if (this == obj)
            {
                return true;
            }

            if (!(obj instanceof KafkaMergedFilter))
            {
                return false;
            }

            KafkaMergedFilter that = (KafkaMergedFilter) obj;
            return Objects.equals(this.conditions, that.conditions);
        }
    }

    private abstract static class KafkaMergedCondition
    {
        private static final class Key extends KafkaMergedCondition
        {
            private final DirectBuffer value;

            private Key(
                DirectBuffer value)
            {
                this.value = value;
            }

            @Override
            protected void set(
                KafkaConditionFW.Builder condition)
            {
                condition.key(this::set);
            }

            private void set(
                KafkaKeyFW.Builder key)
            {
                if (value == null)
                {
                    key.length(-1).value((OctetsFW) null);
                }
                else
                {
                    key.length(value.capacity()).value(value, 0, value.capacity());
                }
            }

            @Override
            public int hashCode()
            {
                return Objects.hash(value);
            }

            @Override
            public boolean equals(Object obj)
            {
                if (this == obj)
                {
                    return true;
                }

                if (!(obj instanceof Key))
                {
                    return false;
                }

                Key that = (Key) obj;
                return Objects.equals(this.value, that.value);
            }
        }

        private static final class Header extends KafkaMergedCondition
        {
            private final DirectBuffer name;
            private final DirectBuffer value;

            private Header(
                DirectBuffer name,
                DirectBuffer value)
            {
                this.name = name;
                this.value = value;
            }

            @Override
            protected void set(
                KafkaConditionFW.Builder condition)
            {
                condition.header(this::set);
            }

            private void set(
                KafkaHeaderFW.Builder header)
            {
                if (name == null)
                {
                    header.nameLen(-1).name((OctetsFW) null);
                }
                else
                {
                    header.nameLen(name.capacity()).name(name, 0, name.capacity());
                }

                if (value == null)
                {
                    header.valueLen(-1).value((OctetsFW) null);
                }
                else
                {
                    header.valueLen(value.capacity()).value(value, 0, value.capacity());
                }
            }

            @Override
            public int hashCode()
            {
                return Objects.hash(name, value);
            }

            @Override
            public boolean equals(
                Object o)
            {
                if (this == o)
                {
                    return true;
                }

                if (!(o instanceof Header))
                {
                    return false;
                }

                final Header that = (Header) o;
                return Objects.equals(this.name, that.name) &&
                       Objects.equals(this.value, that.value);
            }
        }

        private static final class Headers extends KafkaMergedCondition
        {
            private final DirectBuffer name;
            private final DirectBuffer values;

            private final Array32FW<KafkaValueMatchFW> valuesRO = new Array32FW<>(new KafkaValueMatchFW());

            private Headers(
                DirectBuffer name,
                DirectBuffer values)
            {
                this.name = name;
                this.values = values;
            }

            @Override
            protected void set(
                KafkaConditionFW.Builder condition)
            {
                condition.headers(this::set);
            }

            private void set(
                KafkaHeadersFW.Builder headers)
            {
                if (name == null)
                {
                    headers.nameLen(-1).name((OctetsFW) null);
                }
                else
                {
                    headers.nameLen(name.capacity()).name(name, 0, name.capacity());
                }

                if (values == null)
                {
                    headers.values(v -> {});
                }
                else
                {
                    Array32FW<KafkaValueMatchFW> values = valuesRO.wrap(this.values, 0, this.values.capacity());
                    headers.values(values);
                }
            }

            @Override
            public int hashCode()
            {
                return Objects.hash(name, values);
            }

            @Override
            public boolean equals(
                Object o)
            {
                if (this == o)
                {
                    return true;
                }

                if (!(o instanceof Headers))
                {
                    return false;
                }

                final Headers that = (Headers) o;
                return Objects.equals(this.name, that.name) &&
                       Objects.equals(this.values, that.values);
            }
        }

        private static final class Not extends KafkaMergedCondition
        {
            private final KafkaMergedCondition nested;

            private Not(
                KafkaMergedCondition nested)
            {
                this.nested = nested;
            }

            @Override
            protected void set(
                KafkaConditionFW.Builder condition)
            {
                condition.not(this::set);
            }

            private void set(
                KafkaNotFW.Builder not)
            {
                not.condition(nested::set);
            }

            @Override
            public int hashCode()
            {
                return Objects.hash(nested);
            }

            @Override
            public boolean equals(
                Object o)
            {
                if (this == o)
                {
                    return false;
                }

                if (!(o instanceof Not))
                {
                    return false;
                }

                final Not that = (Not) o;
                return Objects.equals(this.nested, that.nested);
            }
        }

        protected abstract void set(KafkaConditionFW.Builder builder);
    }

    private MessageConsumer newStream(
        MessageConsumer sender,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        long affinity,
        Consumer<OctetsFW.Builder> extension)
    {
        final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .authorization(authorization)
                .affinity(affinity)
                .extension(extension)
                .build();

        final MessageConsumer receiver =
                streamFactory.newStream(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof(), sender);

        receiver.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());

        return receiver;
    }

    private void doBegin(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        long affinity,
        Flyweight.Builder.Visitor extension)
    {
        final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .authorization(authorization)
                .affinity(affinity)
                .extension(b -> b.set(extension))
                .build();

        receiver.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());
    }

    private void doBegin(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        long affinity,
        Consumer<OctetsFW.Builder> extension)
    {
        final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .authorization(authorization)
                .affinity(affinity)
                .extension(extension)
                .build();

        receiver.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());
    }

    private void doData(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        long budgetId,
        int reserved,
        int flags,
        OctetsFW payload,
        Flyweight extension)
    {
        final DataFW data = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .authorization(authorization)
                .flags(flags)
                .budgetId(budgetId)
                .reserved(reserved)
                .payload(payload)
                .extension(extension.buffer(), extension.offset(), extension.sizeof())
                .build();

        receiver.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());
    }

    private void doEnd(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        Consumer<OctetsFW.Builder> extension)
    {
        final EndFW end = endRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .authorization(authorization)
                .extension(extension)
                .build();

        receiver.accept(end.typeId(), end.buffer(), end.offset(), end.sizeof());
    }

    private void doFlush(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        int reserved,
        Flyweight extension)
    {
        final FlushFW flush = flushRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .authorization(authorization)
                .budgetId(0L)
                .reserved(reserved)
                .extension(extension.buffer(), extension.offset(), extension.sizeof())
                .build();

        receiver.accept(flush.typeId(), flush.buffer(), flush.offset(), flush.sizeof());
    }

    private void doAbort(
        MessageConsumer receiver,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        Consumer<OctetsFW.Builder> extension)
    {
        final AbortFW abort = abortRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .authorization(authorization)
                .extension(extension)
                .build();

        receiver.accept(abort.typeId(), abort.buffer(), abort.offset(), abort.sizeof());
    }

    private void doWindow(
        MessageConsumer sender,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization,
        long budgetId,
        int padding,
        int minimum)
    {
        final WindowFW window = windowRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .authorization(authorization)
                .budgetId(budgetId)
                .padding(padding)
                .minimum(minimum)
                .build();

        sender.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
    }

    private void doReset(
        MessageConsumer sender,
        long originId,
        long routedId,
        long streamId,
        long sequence,
        long acknowledge,
        int maximum,
        long traceId,
        long authorization)
    {
        final ResetFW reset = resetRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(originId)
                .routedId(routedId)
                .streamId(streamId)
                .sequence(sequence)
                .acknowledge(acknowledge)
                .maximum(maximum)
                .traceId(traceId)
                .authorization(authorization)
                .build();

        sender.accept(reset.typeId(), reset.buffer(), reset.offset(), reset.sizeof());
    }

    private static boolean hasFetchCapability(
        KafkaCapabilities capabilities)
    {
        return (capabilities.value() & FETCH_ONLY.value()) != 0;
    }

    private static boolean hasProduceCapability(
        KafkaCapabilities capabilities)
    {
        return (capabilities.value() & PRODUCE_ONLY.value()) != 0;
    }

    private final class KafkaMergedStream
    {
        private final MessageConsumer sender;
        private final long originId;
        private final long routedId;
        private final long initialId;
        private final long replyId;
        private final long affinity;
        private final long authorization;
        private final String topic;
        private final long resolvedId;
        private final KafkaUnmergedDescribeStream describeStream;
        private final KafkaUnmergedMetaStream metaStream;
        private final List<KafkaUnmergedFetchStream> fetchStreams;
        private final List<KafkaUnmergedProduceStream> produceStreams;
        private final Int2IntHashMap leadersByPartitionId;
        private final Long2LongHashMap latestOffsetByPartitionId;
        private final Long2LongHashMap stableOffsetByPartitionId;
        private final Long2LongHashMap nextOffsetsById;
        private final Long2LongHashMap initialLatestOffsetsById;
        private final Long2LongHashMap initialStableOffsetsById;
        private final long defaultOffset;
        private final KafkaIsolation isolation;
        private final KafkaDeltaType deltaType;
        private final KafkaAckMode ackMode;

        private KafkaOffsetType maximumOffset;
        private List<KafkaMergedFilter> filters;
        private KafkaEvaluationFW evaluation;

        private int state;
        private KafkaCapabilities capabilities;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;
        private int replyMin;
        private int replyPad;
        private long replyBudgetId;

        private int nextNullKeyHash;
        private int fetchStreamIndex;
        private long mergedReplyBudgetId = NO_CREDITOR_INDEX;

        private KafkaUnmergedProduceStream producer;

        KafkaMergedStream(
            MessageConsumer sender,
            long originId,
            long routedId,
            long initialId,
            long affinity,
            long authorization,
            String topic,
            long resolvedId,
            KafkaCapabilities capabilities,
            Long2LongHashMap initialOffsetsById,
            long defaultOffset,
            KafkaIsolation isolation,
            KafkaDeltaType deltaType,
            KafkaAckMode ackMode)
        {
            this.sender = sender;
            this.originId = originId;
            this.routedId = routedId;
            this.initialId = initialId;
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.affinity = affinity;
            this.authorization = authorization;
            this.topic = topic;
            this.resolvedId = resolvedId;
            this.capabilities = capabilities;
            this.describeStream = new KafkaUnmergedDescribeStream(this);
            this.metaStream = new KafkaUnmergedMetaStream(this);
            this.fetchStreams = new ArrayList<>();
            this.produceStreams = new ArrayList<>();
            this.leadersByPartitionId = new Int2IntHashMap(-1);
            this.latestOffsetByPartitionId = new Long2LongHashMap(-3);
            this.stableOffsetByPartitionId = new Long2LongHashMap(-3);
            this.nextOffsetsById = initialOffsetsById;
            this.initialLatestOffsetsById = new Long2LongHashMap(-3);
            this.initialStableOffsetsById = new Long2LongHashMap(-3);
            this.defaultOffset = defaultOffset;
            this.isolation = isolation;
            this.deltaType = deltaType;
            this.ackMode = ackMode;
        }

        private void onMergedMessage(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onMergedInitialBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onMergedInitialData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onMergedInitialEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onMergedInitialAbort(abort);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onMergedInitialFlush(flush);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onMergedReplyWindow(window);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onMergedReplyReset(reset);
                break;
            default:
                break;
            }
        }

        private void onMergedInitialBegin(
            BeginFW begin)
        {
            final long sequence = begin.sequence();
            final long acknowledge = begin.acknowledge();
            final long traceId = begin.traceId();

            assert acknowledge == sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence;
            initialAck = acknowledge;

            assert state == 0;
            state = KafkaState.openingInitial(state);

            final OctetsFW extension = begin.extension();
            final DirectBuffer buffer = extension.buffer();
            final int offset = extension.offset();
            final int limit = extension.limit();

            final KafkaBeginExFW beginEx = kafkaBeginExRO.wrap(buffer, offset, limit);
            final KafkaMergedBeginExFW mergedBeginEx = beginEx.merged();
            final Array32FW<KafkaFilterFW> filters = mergedBeginEx.filters();

            this.maximumOffset = asMaximumOffset(mergedBeginEx.partitions());
            this.filters = asMergedFilters(filters);
            this.evaluation = mergedBeginEx.evaluation();

            describeStream.doDescribeInitialBegin(traceId);
        }

        private void onMergedInitialData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long budgetId = data.budgetId();
            final int reserved = data.reserved();

            assert acknowledge <= sequence;
            assert sequence >= initialSeq;

            initialSeq = sequence + reserved;

            assert initialAck <= initialSeq;

            if (initialSeq > initialAck + initialMax)
            {
                doMergedCleanup(traceId);
            }
            else
            {
                assert hasProduceCapability(capabilities);

                final int flags = data.flags();
                final OctetsFW payload = data.payload();
                final OctetsFW extension = data.extension();

                if (producer == null)
                {
                    assert (flags & FLAGS_INIT) != FLAGS_NONE;

                    final ExtensionFW dataEx = extension.get(extensionRO::tryWrap);
                    final KafkaDataExFW kafkaDataEx = dataEx != null && dataEx.typeId() == kafkaTypeId ?
                            extension.get(kafkaDataExRO::tryWrap) : null;

                    assert kafkaDataEx != null;
                    assert kafkaDataEx.kind() == KafkaDataExFW.KIND_MERGED;
                    final KafkaMergedDataExFW kafkaMergedDataEx = kafkaDataEx.merged();
                    final KafkaKeyFW key = kafkaMergedDataEx.key();
                    final KafkaOffsetFW partition = kafkaMergedDataEx.partition();
                    final int partitionId = partition.partitionId();
                    final int nextPartitionId = partitionId == DYNAMIC_PARTITION ? nextPartition(key) : partitionId;

                    final KafkaUnmergedProduceStream newProducer = findProducePartitionLeader(nextPartitionId);
                    assert newProducer != null; // TODO
                    this.producer = newProducer;
                }

                assert producer != null;

                producer.doProduceInitialData(traceId, reserved, flags, budgetId, payload, extension);

                if ((flags & FLAGS_FIN) != FLAGS_NONE)
                {
                    this.producer = null;
                }
            }
        }

        private KafkaOffsetType asMaximumOffset(
            Array32FW<KafkaOffsetFW> partitions)
        {
            return partitions.isEmpty() ||
                   partitions.anyMatch(p -> p.latestOffset() != HISTORICAL.value()) ? LIVE : HISTORICAL;
        }

        private int nextPartition(
            KafkaKeyFW key)
        {
            final int partitionCount = leadersByPartitionId.size();
            final int keyHash = key.length() != -1 ? defaultKeyHash(key) : nextNullKeyHash++;
            final int partitionId = partitionCount > 0 ? (0x7fff_ffff & keyHash) % partitionCount : 0;

            return partitionId;
        }

        private void onMergedInitialEnd(
            EndFW end)
        {
            final long traceId = end.traceId();

            assert !KafkaState.initialClosed(state);
            state = KafkaState.closedInitial(state);

            describeStream.doDescribeInitialEndIfNecessary(traceId);
            metaStream.doMetaInitialEndIfNecessary(traceId);
            fetchStreams.forEach(f -> f.onMergedInitialEnd(traceId));
            produceStreams.forEach(f -> f.doProduceInitialEndIfNecessary(traceId));

            if (fetchStreams.isEmpty())
            {
                doMergedReplyEndIfNecessary(traceId);
            }
        }

        private void onMergedInitialAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();

            assert !KafkaState.initialClosed(state);
            state = KafkaState.closedInitial(state);

            describeStream.doDescribeInitialAbortIfNecessary(traceId);
            metaStream.doMetaInitialAbortIfNecessary(traceId);
            fetchStreams.forEach(f -> f.onMergedInitialAbort(traceId));
            produceStreams.forEach(f -> f.doProduceInitialEndIfNecessary(traceId));

            if (fetchStreams.isEmpty())
            {
                doMergedReplyAbortIfNecessary(traceId);
            }
        }

        private void onMergedInitialFlush(
            FlushFW flush)
        {
            final long traceId = flush.traceId();
            final OctetsFW extension = flush.extension();
            final ExtensionFW flushEx = extension.get(extensionRO::tryWrap);

            final KafkaFlushExFW kafkaFlushEx = flushEx != null && flushEx.typeId() == kafkaTypeId ?
                    extension.get(kafkaFlushExRO::tryWrap) : null;

            assert kafkaFlushEx != null;
            assert kafkaFlushEx.kind() == KafkaFlushExFW.KIND_MERGED;
            final KafkaMergedFlushExFW kafkaMergedFlushEx = kafkaFlushEx.merged();
            final KafkaCapabilities newCapabilities = kafkaMergedFlushEx.capabilities().get();

            if (capabilities != newCapabilities)
            {
                final Array32FW<KafkaFilterFW> filters = kafkaMergedFlushEx.filters();
                final List<KafkaMergedFilter> newFilters = asMergedFilters(filters);

                this.maximumOffset = asMaximumOffset(kafkaMergedFlushEx.progress());

                if (hasFetchCapability(capabilities) && hasFetchCapability(newCapabilities))
                {
                    assert Objects.equals(this.filters, newFilters);
                }

                if (hasFetchCapability(newCapabilities) && !hasFetchCapability(capabilities))
                {
                    final Long2LongHashMap initialOffsetsById = new Long2LongHashMap(-3L);
                    kafkaMergedFlushEx.progress().forEach(p ->
                    {
                        final long partitionId = p.partitionId();
                        if (partitionId >= 0L)
                        {
                            final long partitionOffset = p.partitionOffset();
                            initialOffsetsById.put(partitionId, partitionOffset);
                        }
                    });

                    nextOffsetsById.clear();
                    nextOffsetsById.putAll(initialOffsetsById);
                }

                this.capabilities = newCapabilities;
                this.filters = newFilters;

                doFetchPartitionsIfNecessary(traceId);
                doProducePartitionsIfNecessary(traceId);
            }
        }

        private void onMergedReplyWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long traceId = window.traceId();
            final long budgetId = window.budgetId();
            final int padding = window.padding();
            final int minimum = window.minimum();

            assert acknowledge <= sequence;
            assert sequence <= replySeq;
            assert acknowledge >= replyAck;
            assert maximum >= replyMax;

            final int replyNoAck = (int)(replySeq - replyAck);
            final int newReplyNoAck = (int)(sequence - acknowledge);
            final int credit = (replyNoAck - newReplyNoAck) + (maximum - replyMax);
            assert credit >= 0;

            this.replyAck = acknowledge;
            this.replyMax = maximum;
            this.replyMin = minimum;
            this.replyPad = padding;
            this.replyBudgetId = budgetId;

            assert replyAck <= replySeq;

            if (KafkaState.replyOpening(state))
            {
                state = KafkaState.openedReply(state);
                if (mergedReplyBudgetId == NO_CREDITOR_INDEX)
                {
                    mergedReplyBudgetId = creditor.acquire(replyId, budgetId);
                }
            }

            if (mergedReplyBudgetId != NO_CREDITOR_INDEX)
            {
                creditor.credit(traceId, mergedReplyBudgetId, credit);
            }

            doUnmergedFetchReplyWindowsIfNecessary(traceId);
        }

        private void doUnmergedFetchReplyWindowsIfNecessary(
            long traceId)
        {
            if (KafkaState.replyOpened(state))
            {
                final int fetchStreamCount = fetchStreams.size();
                if (fetchStreamIndex >= fetchStreamCount)
                {
                    fetchStreamIndex = 0;
                }

                for (int index = fetchStreamIndex; index < fetchStreamCount; index++)
                {
                    final KafkaUnmergedFetchStream fetchStream = fetchStreams.get(index);
                    fetchStream.doFetchReplyWindowIfNecessary(traceId);
                }

                for (int index = 0; index < fetchStreamIndex; index++)
                {
                    final KafkaUnmergedFetchStream fetchStream = fetchStreams.get(index);
                    fetchStream.doFetchReplyWindowIfNecessary(traceId);
                }

                fetchStreamIndex++;
            }
        }

        private void onMergedReplyReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            state = KafkaState.closedReply(state);
            nextOffsetsById.clear();

            describeStream.doDescribeReplyResetIfNecessary(traceId);
            metaStream.doMetaReplyResetIfNecessary(traceId);
            fetchStreams.forEach(f -> f.onMergedReplyReset(traceId));
            produceStreams.forEach(f -> f.doProduceReplyResetIfNecessary(traceId));

            if (fetchStreams.isEmpty())
            {
                doMergedInitialResetIfNecessary(traceId);
            }
        }

        private void doMergedReplyBeginIfNecessary(
            long traceId)
        {
            if (!KafkaState.replyOpening(state))
            {
                doMergedReplyBegin(traceId);
            }
        }

        private void doMergedReplyBegin(
            long traceId)
        {
            assert !KafkaState.replyOpening(state);
            state = KafkaState.openingReply(state);

            final int replyBudget = replyMax - (int)(replySeq - replyAck);
            if (!KafkaState.replyOpened(state) && replyBudget > 0)
            {
                state = KafkaState.openedReply(state);
                if (mergedReplyBudgetId == NO_CREDITOR_INDEX)
                {
                    mergedReplyBudgetId = creditor.acquire(replyId, replyBudgetId);
                    creditor.credit(traceId, mergedReplyBudgetId, replyBudget);
                }
            }

            if (capabilities == FETCH_ONLY)
            {
                doBegin(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                        traceId, authorization, affinity, httpBeginExToKafka());
            }
            else
            {
                doBegin(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                        traceId, authorization, affinity, EMPTY_EXTENSION);
            }

            doUnmergedFetchReplyWindowsIfNecessary(traceId);
        }

        private Flyweight.Builder.Visitor httpBeginExToKafka()
        {
            return (buffer, offset, maxLimit) ->
                kafkaBeginExRW.wrap(buffer, offset, maxLimit)
                              .typeId(kafkaTypeId)
                              .merged(beginExToKafkaMerged())
                              .build()
                              .limit() - offset;
        }

        private Consumer<KafkaMergedBeginExFW.Builder> beginExToKafkaMerged()
        {
            return builder ->
            {
                builder.capabilities(c -> c.set(FETCH_ONLY)).topic(topic);
                latestOffsetByPartitionId.longForEach((k, v) -> builder
                    .partitionsItem(i -> i
                        .partitionId((int) k)
                        .partitionOffset(0L)
                        .stableOffset(stableOffsetByPartitionId.get(k))
                        .latestOffset(v)));
            };
        }

        private void doMergedReplyData(
            long traceId,
            int flags,
            int reserved,
            OctetsFW payload,
            KafkaDataExFW kafkaDataEx)
        {
            Flyweight newKafkaDataEx = EMPTY_OCTETS;

            if (flags != 0x00)
            {
                assert kafkaDataEx != null;

                final KafkaFetchDataExFW kafkaFetchDataEx = kafkaDataEx.fetch();
                final KafkaOffsetFW partition = kafkaFetchDataEx.partition();
                final int partitionId = partition.partitionId();
                final long partitionOffset = partition.partitionOffset();
                final long latestOffset = partition.latestOffset();
                final int deferred = kafkaFetchDataEx.deferred();
                final long timestamp = kafkaFetchDataEx.timestamp();
                final KafkaKeyFW key = kafkaFetchDataEx.key();
                final ArrayFW<KafkaHeaderFW> headers = kafkaFetchDataEx.headers();
                final KafkaDeltaFW delta = kafkaFetchDataEx.delta();
                final long filters = kafkaFetchDataEx.filters();

                nextOffsetsById.put(partitionId, partitionOffset + 1);

                newKafkaDataEx = kafkaDataExRW.wrap(extBuffer, 0, extBuffer.capacity())
                     .typeId(kafkaTypeId)
                     .merged(f -> f.deferred(deferred)
                                   .timestamp(timestamp)
                                   .filters(filters)
                                   .partition(p -> p.partitionId(partitionId)
                                                    .partitionOffset(partitionOffset)
                                                    .latestOffset(latestOffset))
                                   .progress(ps -> nextOffsetsById.longForEach((p, o) -> ps.item(i -> i.partitionId((int) p)
                                                                                                       .partitionOffset(o))))
                                   .key(k -> k.length(key.length())
                                              .value(key.value()))
                                   .delta(d -> d.type(t -> t.set(delta.type())).ancestorOffset(delta.ancestorOffset()))
                                   .headers(hs -> headers.forEach(h -> hs.item(i -> i.nameLen(h.nameLen())
                                                                                     .name(h.name())
                                                                                     .valueLen(h.valueLen())
                                                                                     .value(h.value())))))
                     .build();
            }

            doData(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, replyBudgetId, reserved, flags, payload, newKafkaDataEx);

            replySeq += reserved;

            assert replyAck <= replySeq;
        }

        private void doMergedReplyEnd(
            long traceId)
        {
            assert !KafkaState.replyClosed(state);
            state = KafkaState.closedReply(state);
            cleanupBudgetCreditorIfNecessary();
            doEnd(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, EMPTY_EXTENSION);
        }

        private void doMergedReplyAbort(
            long traceId)
        {
            assert !KafkaState.replyClosed(state);
            state = KafkaState.closedReply(state);
            cleanupBudgetCreditorIfNecessary();
            doAbort(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                    traceId, authorization, EMPTY_EXTENSION);
        }

        private void doMergedInitialWindow(
            long traceId,
            long budgetId)
        {
            int maxInitialNoAck = 0;
            int maxInitialPad = 0;
            int minInitialMax = 0;

            // TODO: sync on write, not sync on read
            if (!produceStreams.isEmpty())
            {
                initialNoAckRW.value = 0;
                initialPadRW.value = 0;
                initialMaxRW.value = Integer.MAX_VALUE;
                produceStreams.forEach(p -> initialNoAckRW.value = Math.max(p.initialNoAck(), initialNoAckRW.value));
                produceStreams.forEach(p -> initialPadRW.value = Math.max(p.initialPad, initialPadRW.value));
                produceStreams.forEach(p -> initialMaxRW.value = Math.min(p.initialMax, initialMaxRW.value));
                maxInitialNoAck = initialNoAckRW.value;
                maxInitialPad = initialPadRW.value;
                minInitialMax = initialMaxRW.value;
            }

            final long newInitialAck = Math.max(initialSeq - maxInitialNoAck, initialAck);

            if (newInitialAck > initialAck || minInitialMax > initialMax ||
                !KafkaState.initialOpened(state) && !hasProduceCapability(capabilities))
            {
                initialAck = newInitialAck;
                assert initialAck <= initialSeq;

                initialMax = minInitialMax;

                state = KafkaState.openedInitial(state);

                doWindow(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                        traceId, authorization, budgetId, maxInitialPad, DEFAULT_MINIMUM);
            }
        }

        private void doMergedInitialReset(
            long traceId)
        {
            assert !KafkaState.initialClosed(state);
            state = KafkaState.closedInitial(state);

            doReset(sender, originId, routedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, authorization);
        }

        private void doMergedReplyEndIfNecessary(
            long traceId)
        {
            if (KafkaState.replyOpening(state) &&
                !KafkaState.replyClosed(state))
            {
                describeStream.doDescribeReplyResetIfNecessary(traceId);
                metaStream.doMetaReplyResetIfNecessary(traceId);
                produceStreams.forEach(f -> f.doProduceReplyResetIfNecessary(traceId));

                state = KafkaState.closingReply(state);
                nextOffsetsById.clear();
                fetchStreams.forEach(f -> f.doFetchInitialAbortIfNecessary(traceId));
                if (fetchStreams.isEmpty())
                {
                    doMergedReplyEnd(traceId);
                }
            }
        }

        private void doMergedReplyFlush(
                long traceId,
                int reserved,
                KafkaFlushExFW kafkaFlushEx)
        {
            final KafkaFetchFlushExFW kafkaFetchFlushEx = kafkaFlushEx.fetch();
            kafkaFetchFlushEx.partition().partitionId();
            final KafkaOffsetFW partition = kafkaFetchFlushEx.partition();
            final int partitionId = partition.partitionId();
            final long partitionOffset = partition.partitionOffset();
            final long latestOffset = partition.latestOffset();
            final long stableOffset = partition.stableOffset();

            nextOffsetsById.put(partitionId, partitionOffset);
            initialLatestOffsetsById.put(partitionId, latestOffset);
            initialStableOffsetsById.put(partitionId, stableOffset);

            if (nextOffsetsById.size() == fetchStreams.size() &&
                initialLatestOffsetsById.size() == fetchStreams.size())
            {
                final KafkaFlushExFW kafkaFlushExFW = kafkaFlushExRW.wrap(extBuffer, 0, extBuffer.capacity())
                        .typeId(kafkaTypeId)
                        .merged(f -> f
                                .progress(ps -> nextOffsetsById.longForEach((p, o) ->
                                        ps.item(i -> i.partitionId((int) p)
                                                .partitionOffset(o)
                                                .stableOffset(initialStableOffsetsById.get(p))
                                                .latestOffset(initialLatestOffsetsById.get(p))))))
                        .build();

                doFlush(sender, originId, routedId, replyId, replySeq, replyAck, replyMax,
                        traceId, authorization, reserved, kafkaFlushExFW);

                replySeq += reserved;

                assert replyAck <= replySeq;
            }
        }

        private void doMergedReplyAbortIfNecessary(
            long traceId)
        {
            if (!KafkaState.replyClosed(state))
            {
                if (KafkaState.replyOpening(state))
                {
                    doMergedReplyAbort(traceId);
                    describeStream.doDescribeReplyResetIfNecessary(traceId);
                    metaStream.doMetaReplyResetIfNecessary(traceId);
                    fetchStreams.forEach(f -> f.doFetchReplyResetIfNecessary(traceId));
                    produceStreams.forEach(f -> f.doProduceReplyResetIfNecessary(traceId));
                }
                else
                {
                    detachSender.accept(replyId);
                }
            }
        }

        private void doMergedInitialResetIfNecessary(
            long traceId)
        {
            if (KafkaState.initialOpening(state) &&
                !KafkaState.initialClosed(state))
            {
                describeStream.doDescribeInitialAbortIfNecessary(traceId);
                metaStream.doMetaInitialAbortIfNecessary(traceId);
                produceStreams.forEach(f -> f.doProduceInitialAbortIfNecessary(traceId));

                state = KafkaState.closingInitial(state);
                nextOffsetsById.clear();
                fetchStreams.forEach(f -> f.doFetchReplyResetIfNecessary(traceId));
                creditor.cleanupChild(mergedReplyBudgetId);
                cleanupBudgetCreditorIfNecessary();
                if (fetchStreams.isEmpty())
                {
                    doMergedInitialReset(traceId);
                }
            }
        }

        private void doMergedCleanup(
            long traceId)
        {
            doMergedInitialResetIfNecessary(traceId);
            doMergedReplyAbortIfNecessary(traceId);
        }

        private void cleanupBudgetCreditorIfNecessary()
        {
            if (mergedReplyBudgetId != NO_CREDITOR_INDEX)
            {
                creditor.release(mergedReplyBudgetId);
                mergedReplyBudgetId = NO_CREDITOR_INDEX;
            }
        }

        private void onTopicConfigChanged(
            long traceId,
            ArrayFW<KafkaConfigFW> configs)
        {
            metaStream.doMetaInitialBeginIfNecessary(traceId);
        }

        private void onTopicMetaDataChanged(
            long traceId,
            ArrayFW<KafkaPartitionFW> partitions)
        {
            leadersByPartitionId.clear();
            partitions.forEach(p -> leadersByPartitionId.put(p.partitionId(), p.leaderId()));
            partitionCount.value = 0;
            partitions.forEach(partition -> partitionCount.value++);
            assert leadersByPartitionId.size() == partitionCount.value;

            doFetchPartitionsIfNecessary(traceId);
            doProducePartitionsIfNecessary(traceId);
        }

        private void doFetchPartitionsIfNecessary(
            long traceId)
        {
            if (hasFetchCapability(capabilities))
            {
                final int partitionCount = leadersByPartitionId.size();
                for (int partitionId = 0; partitionId < partitionCount; partitionId++)
                {
                    doFetchPartitionIfNecessary(traceId, partitionId);
                }
                assert fetchStreams.size() >= leadersByPartitionId.size();

                int offsetCount = nextOffsetsById.size();
                for (int partitionId = partitionCount; partitionId < offsetCount; partitionId++)
                {
                    nextOffsetsById.remove(partitionId);
                }
                assert nextOffsetsById.size() <= leadersByPartitionId.size();
            }
        }

        private void doProducePartitionsIfNecessary(
            long traceId)
        {
            if (hasProduceCapability(capabilities))
            {
                final int partitionCount = leadersByPartitionId.size();
                for (int partitionId = 0; partitionId < partitionCount; partitionId++)
                {
                    doProducePartitionIfNecessary(traceId, partitionId);
                }
                assert produceStreams.size() >= leadersByPartitionId.size();
            }
        }

        private void doFetchPartitionIfNecessary(
            long traceId,
            int partitionId)
        {
            final int leaderId = leadersByPartitionId.get(partitionId);
            final long partitionOffset = nextFetchPartitionOffset(partitionId);

            KafkaUnmergedFetchStream leader = findFetchPartitionLeader(partitionId);

            if (leader != null && leader.leaderId != leaderId)
            {
                leader.leaderId = leaderId;
                leader.doFetchInitialBeginIfNecessary(traceId, partitionOffset);
            }

            if (leader == null)
            {
                leader = new KafkaUnmergedFetchStream(partitionId, leaderId, this);
                leader.doFetchInitialBegin(traceId, partitionOffset);
                fetchStreams.add(leader);
            }

            assert leader != null;
            assert leader.partitionId == partitionId;
            assert leader.leaderId == leaderId;
        }

        private void onFetchPartitionLeaderReady(
            long traceId,
            long partitionId,
            long stableOffset,
            long latestOffset)
        {
            nextOffsetsById.putIfAbsent(partitionId, defaultOffset);
            latestOffsetByPartitionId.put(partitionId, latestOffset);
            stableOffsetByPartitionId.put(partitionId, stableOffset);

            if (nextOffsetsById.size() == fetchStreams.size() &&
                latestOffsetByPartitionId.size() == fetchStreams.size())
            {
                doMergedReplyBeginIfNecessary(traceId);

                if (KafkaState.initialClosed(state))
                {
                    doMergedReplyEndIfNecessary(traceId);
                }
            }
        }

        private void onFetchPartitionLeaderError(
            long traceId,
            int partitionId,
            int error)
        {
            if (error == ERROR_NOT_LEADER_FOR_PARTITION)
            {
                final KafkaUnmergedFetchStream leader = findFetchPartitionLeader(partitionId);
                assert leader != null;

                if (nextOffsetsById.containsKey(partitionId) && maximumOffset != HISTORICAL)
                {
                    final long partitionOffset = nextFetchPartitionOffset(partitionId);
                    leader.doFetchInitialBegin(traceId, partitionOffset);
                }
                else
                {
                    fetchStreams.remove(leader);
                    if (fetchStreams.isEmpty())
                    {
                        if (KafkaState.closed(state))
                        {
                            cleanupBudgetCreditorIfNecessary();
                        }

                        if (KafkaState.initialClosing(state))
                        {
                            doMergedInitialResetIfNecessary(traceId);
                        }
                        if (KafkaState.replyClosing(state))
                        {
                            doMergedReplyEndIfNecessary(traceId);
                        }

                    }
                }
            }
            else
            {
                doMergedInitialResetIfNecessary(traceId);
            }
        }

        private long nextFetchPartitionOffset(
            int partitionId)
        {
            long partitionOffset = nextOffsetsById.get(partitionId);
            if (partitionOffset == nextOffsetsById.missingValue())
            {
                partitionOffset = defaultOffset;
            }
            return partitionOffset;
        }

        private KafkaUnmergedFetchStream findFetchPartitionLeader(
            int partitionId)
        {
            KafkaUnmergedFetchStream leader = null;
            for (int index = 0; index < fetchStreams.size(); index++)
            {
                final KafkaUnmergedFetchStream fetchStream = fetchStreams.get(index);
                if (fetchStream.partitionId == partitionId)
                {
                    leader = fetchStream;
                    break;
                }
            }
            return leader;
        }

        private void doProducePartitionIfNecessary(
            long traceId,
            int partitionId)
        {
            final int leaderId = leadersByPartitionId.get(partitionId);

            KafkaUnmergedProduceStream leader = findProducePartitionLeader(partitionId);

            if (leader != null && leader.leaderId != leaderId)
            {
                leader.leaderId = leaderId;
                leader.doProduceInitialBeginIfNecessary(traceId);
            }

            if (leader == null)
            {
                leader = new KafkaUnmergedProduceStream(partitionId, leaderId, this);
                leader.doProduceInitialBegin(traceId);
                produceStreams.add(leader);
            }

            assert leader != null;
            assert leader.partitionId == partitionId;
            assert leader.leaderId == leaderId;
        }

        private void onProducePartitionLeaderReady(
            long traceId,
            long partitionId)
        {
            if (produceStreams.size() == leadersByPartitionId.size())
            {
                if (!KafkaState.initialOpened(state))
                {
                    doMergedInitialWindow(traceId, 0L);
                }

                doMergedReplyBeginIfNecessary(traceId);

                if (KafkaState.initialClosed(state))
                {
                    doMergedReplyEndIfNecessary(traceId);
                }
            }
        }

        private void onProducePartitionLeaderError(
            long traceId,
            int partitionId,
            int error)
        {
            if (error == ERROR_NOT_LEADER_FOR_PARTITION)
            {
                final KafkaUnmergedProduceStream leader = findProducePartitionLeader(partitionId);
                assert leader != null;

                if (leadersByPartitionId.containsKey(partitionId))
                {
                    leader.doProduceInitialBegin(traceId);
                }
                else
                {
                    produceStreams.remove(leader);
                }
            }
            else
            {
                doMergedInitialResetIfNecessary(traceId);
            }
        }

        private KafkaUnmergedProduceStream findProducePartitionLeader(
            int partitionId)
        {
            KafkaUnmergedProduceStream leader = null;
            for (int index = 0; index < produceStreams.size(); index++)
            {
                final KafkaUnmergedProduceStream produceStream = produceStreams.get(index);
                if (produceStream.partitionId == partitionId)
                {
                    leader = produceStream;
                    break;
                }
            }
            return leader;
        }
    }

    private final class KafkaUnmergedDescribeStream
    {
        private final KafkaMergedStream merged;

        private long initialId;
        private long replyId;
        private MessageConsumer receiver = NO_RECEIVER;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private KafkaUnmergedDescribeStream(
            KafkaMergedStream merged)
        {
            this.merged = merged;
        }

        private void doDescribeInitialBegin(
            long traceId)
        {
            assert state == 0;

            state = KafkaState.openingInitial(state);

            this.initialId = supplyInitialId.applyAsLong(merged.resolvedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.receiver = newStream(this::onDescribeReply,
                merged.routedId, merged.resolvedId, initialId, initialSeq, initialAck, initialMax,
                traceId, merged.authorization, 0L,
                ex -> ex.set((b, o, l) -> kafkaBeginExRW.wrap(b, o, l)
                        .typeId(kafkaTypeId)
                        .describe(m -> m.topic(merged.topic)
                                        .configsItem(ci -> ci.set(CONFIG_NAME_CLEANUP_POLICY))
                                        .configsItem(ci -> ci.set(CONFIG_NAME_MAX_MESSAGE_BYTES))
                                        .configsItem(ci -> ci.set(CONFIG_NAME_SEGMENT_BYTES))
                                        .configsItem(ci -> ci.set(CONFIG_NAME_SEGMENT_INDEX_BYTES))
                                        .configsItem(ci -> ci.set(CONFIG_NAME_SEGMENT_MILLIS))
                                        .configsItem(ci -> ci.set(CONFIG_NAME_RETENTION_BYTES))
                                        .configsItem(ci -> ci.set(CONFIG_NAME_RETENTION_MILLIS))
                                        .configsItem(ci -> ci.set(CONFIG_NAME_DELETE_RETENTION_MILLIS))
                                        .configsItem(ci -> ci.set(CONFIG_NAME_MIN_COMPACTION_LAG_MILLIS))
                                        .configsItem(ci -> ci.set(CONFIG_NAME_MAX_COMPACTION_LAG_MILLIS))
                                        .configsItem(ci -> ci.set(CONFIG_NAME_MIN_CLEANABLE_DIRTY_RATIO)))
                        .build()
                        .sizeof()));
        }

        private void doDescribeInitialEndIfNecessary(
            long traceId)
        {
            if (!KafkaState.initialClosed(state))
            {
                doDescribeInitialEnd(traceId);
            }
        }

        private void doDescribeInitialEnd(
            long traceId)
        {
            state = KafkaState.closedInitial(state);

            doEnd(receiver, merged.routedId, merged.resolvedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, merged.authorization, EMPTY_EXTENSION);
        }

        private void doDescribeInitialAbortIfNecessary(
            long traceId)
        {
            if (!KafkaState.initialClosed(state))
            {
                doDescribeInitialAbort(traceId);
            }
        }

        private void doDescribeInitialAbort(
            long traceId)
        {
            state = KafkaState.closedInitial(state);

            doAbort(receiver, merged.routedId, merged.resolvedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, merged.authorization, EMPTY_EXTENSION);
        }

        private void onDescribeReply(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onDescribeReplyBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onDescribeReplyData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onDescribeReplyEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onDescribeReplyAbort(abort);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onDescribeInitialReset(reset);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onDescribeInitialWindow(window);
                break;
            default:
                break;
            }
        }

        private void onDescribeReplyBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();

            state = KafkaState.openingReply(state);

            doDescribeReplyWindow(traceId, 0, 8192);
        }

        private void onDescribeReplyData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final int reserved = data.reserved();
            final OctetsFW extension = data.extension();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence + reserved;

            assert replyAck <= replySeq;

            if (replySeq > replyAck + replyMax)
            {
                merged.doMergedCleanup(traceId);
            }
            else
            {
                final KafkaDataExFW kafkaDataEx = extension.get(kafkaDataExRO::wrap);
                final KafkaDescribeDataExFW kafkaDescribeDataEx = kafkaDataEx.describe();
                final ArrayFW<KafkaConfigFW> configs = kafkaDescribeDataEx.configs();
                merged.onTopicConfigChanged(traceId, configs);

                doDescribeReplyWindow(traceId, 0, replyMax);
            }
        }

        private void onDescribeReplyEnd(
            EndFW end)
        {
            final long traceId = end.traceId();

            state = KafkaState.closedReply(state);

            merged.doMergedReplyBeginIfNecessary(traceId);
            merged.doMergedReplyEndIfNecessary(traceId);

            doDescribeInitialEndIfNecessary(traceId);
        }

        private void onDescribeReplyAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();

            state = KafkaState.closedReply(state);

            merged.doMergedReplyAbortIfNecessary(traceId);

            doDescribeInitialAbortIfNecessary(traceId);
        }

        private void onDescribeInitialReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            state = KafkaState.closedInitial(state);

            merged.doMergedInitialResetIfNecessary(traceId);

            doDescribeReplyResetIfNecessary(traceId);
        }

        private void onDescribeInitialWindow(
            WindowFW window)
        {
            if (!KafkaState.initialOpened(state))
            {
                final long traceId = window.traceId();

                state = KafkaState.openedInitial(state);

                merged.doMergedInitialWindow(traceId, 0L);
            }
        }

        private void doDescribeReplyWindow(
            long traceId,
            int minReplyNoAck,
            int minReplyMax)
        {
            final long newReplyAck = Math.max(replySeq - minReplyNoAck, replyAck);

            if (newReplyAck > replyAck || minReplyMax > replyMax || !KafkaState.replyOpened(state))
            {
                replyAck = newReplyAck;
                assert replyAck <= replySeq;

                replyMax = minReplyMax;

                state = KafkaState.openedReply(state);

                doWindow(receiver, merged.routedId, merged.resolvedId, replyId, replySeq, replyAck, replyMax,
                        traceId, merged.authorization, 0L, merged.replyPad, DEFAULT_MINIMUM);
            }
        }

        private void doDescribeReplyResetIfNecessary(
            long traceId)
        {
            if (!KafkaState.replyClosed(state))
            {
                doDescribeReplyReset(traceId);
            }
        }

        private void doDescribeReplyReset(
            long traceId)
        {
            state = KafkaState.closedReply(state);

            doReset(receiver, merged.routedId, merged.resolvedId, replyId, replySeq, replyAck, replyMax,
                    traceId, merged.authorization);
        }
    }

    private final class KafkaUnmergedMetaStream
    {
        private final KafkaMergedStream merged;

        private long initialId;
        private long replyId;
        private MessageConsumer receiver = NO_RECEIVER;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private KafkaUnmergedMetaStream(
            KafkaMergedStream merged)
        {
            this.merged = merged;
        }

        private void doMetaInitialBeginIfNecessary(
            long traceId)
        {
            if (!KafkaState.initialOpening(state))
            {
                doMetaInitialBegin(traceId);
            }
        }

        private void doMetaInitialBegin(
            long traceId)
        {
            assert state == 0;

            state = KafkaState.openingInitial(state);

            this.initialId = supplyInitialId.applyAsLong(merged.resolvedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.receiver = newStream(this::onMetaReply,
                merged.routedId,  merged.resolvedId, initialId, initialSeq, initialAck, initialMax,
                traceId, merged.authorization, 0L,
                ex -> ex.set((b, o, l) -> kafkaBeginExRW.wrap(b, o, l)
                        .typeId(kafkaTypeId)
                        .meta(m -> m.topic(merged.topic))
                        .build()
                        .sizeof()));
        }

        private void doMetaInitialEndIfNecessary(
            long traceId)
        {
            if (!KafkaState.initialClosed(state))
            {
                doMetaInitialEnd(traceId);
            }
        }

        private void doMetaInitialEnd(
            long traceId)
        {
            state = KafkaState.closedInitial(state);

            doEnd(receiver, merged.routedId, merged.resolvedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, merged.authorization, EMPTY_EXTENSION);
        }

        private void doMetaInitialAbortIfNecessary(
            long traceId)
        {
            if (KafkaState.initialOpening(state) && !KafkaState.initialClosed(state))
            {
                doMetaInitialAbort(traceId);
            }
        }

        private void doMetaInitialAbort(
            long traceId)
        {
            state = KafkaState.closedInitial(state);

            doAbort(receiver, merged.routedId, merged.resolvedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, merged.authorization, EMPTY_EXTENSION);
        }

        private void onMetaReply(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onMetaReplyBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onMetaReplyData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onMetaReplyEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onMetaReplyAbort(abort);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onMetaInitialReset(reset);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onMetaInitialWindow(window);
                break;
            default:
                break;
            }
        }

        private void onMetaReplyBegin(
            BeginFW begin)
        {
            final long traceId = begin.traceId();

            state = KafkaState.openingReply(state);

            doMetaReplyWindow(traceId, 0, 8192);
        }

        private void onMetaReplyData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final int reserved = data.reserved();
            final OctetsFW extension = data.extension();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence + reserved;

            assert replyAck <= replySeq;

            if (replySeq > replyAck + replyMax)
            {
                merged.doMergedCleanup(traceId);
            }
            else
            {
                final KafkaDataExFW kafkaDataEx = extension.get(kafkaDataExRO::wrap);
                final KafkaMetaDataExFW kafkaMetaDataEx = kafkaDataEx.meta();
                final ArrayFW<KafkaPartitionFW> partitions = kafkaMetaDataEx.partitions();
                merged.onTopicMetaDataChanged(traceId, partitions);

                doMetaReplyWindow(traceId, 0, replyMax);
            }
        }

        private void onMetaReplyEnd(
            EndFW end)
        {
            final long traceId = end.traceId();

            state = KafkaState.closedReply(state);

            merged.doMergedReplyBeginIfNecessary(traceId);
            merged.doMergedReplyEndIfNecessary(traceId);

            doMetaInitialEndIfNecessary(traceId);
        }

        private void onMetaReplyAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();

            state = KafkaState.closedReply(state);

            merged.doMergedReplyAbortIfNecessary(traceId);

            doMetaInitialAbortIfNecessary(traceId);
        }

        private void onMetaInitialReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();

            state = KafkaState.closedInitial(state);

            merged.doMergedInitialResetIfNecessary(traceId);

            doMetaReplyResetIfNecessary(traceId);
        }

        private void onMetaInitialWindow(
            WindowFW window)
        {
            if (!KafkaState.initialOpened(state))
            {
                final long traceId = window.traceId();

                state = KafkaState.openedInitial(state);

                merged.doMergedInitialWindow(traceId, 0L);
            }
        }

        private void doMetaReplyWindow(
            long traceId,
            int minReplyNoAck,
            int minReplyMax)
        {
            final long newReplyAck = Math.max(replySeq - minReplyNoAck, replyAck);

            if (newReplyAck > replyAck || minReplyMax > replyMax || !KafkaState.replyOpened(state))
            {
                replyAck = newReplyAck;
                assert replyAck <= replySeq;

                replyMax = minReplyMax;

                state = KafkaState.openedReply(state);

                doWindow(receiver, merged.routedId, merged.resolvedId, replyId, replySeq, replyAck, replyMax,
                        traceId, merged.authorization, 0L, merged.replyPad, DEFAULT_MINIMUM);
            }
        }

        private void doMetaReplyResetIfNecessary(
            long traceId)
        {
            if (!KafkaState.replyClosed(state))
            {
                doMetaReplyReset(traceId);
            }
        }

        private void doMetaReplyReset(
            long traceId)
        {
            state = KafkaState.closedReply(state);

            doReset(receiver, merged.routedId, merged.resolvedId, replyId, replySeq, replyAck, replyMax,
                    traceId, merged.authorization);
        }
    }

    private final class KafkaUnmergedFetchStream
    {
        private final int partitionId;
        private final KafkaMergedStream merged;

        private int leaderId;

        private long initialId;
        private long replyId;
        private MessageConsumer receiver = NO_RECEIVER;

        private int state;

        private long initialSeq;
        private long initialAck;
        private int initialMax;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private KafkaUnmergedFetchStream(
            int partitionId,
            int leaderId,
            KafkaMergedStream merged)
        {
            this.partitionId = partitionId;
            this.leaderId = leaderId;
            this.merged = merged;
        }

        private void doFetchInitialBeginIfNecessary(
            long traceId,
            long partitionOffset)
        {
            if (!KafkaState.initialOpening(state))
            {
                doFetchInitialBegin(traceId, partitionOffset);
            }
        }

        private void doFetchInitialBegin(
            long traceId,
            long partitionOffset)
        {
            if (KafkaState.closed(state))
            {
                state = 0;
            }

            assert state == 0;

            state = KafkaState.openingInitial(state);

            this.initialId = supplyInitialId.applyAsLong(merged.resolvedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.receiver = newStream(this::onFetchReply,
                merged.routedId, merged.resolvedId, initialId, initialSeq, initialAck, initialMax,
                traceId, merged.authorization, leaderId,
                ex -> ex.set((b, o, l) -> kafkaBeginExRW.wrap(b, o, l)
                        .typeId(kafkaTypeId)
                        .fetch(f -> f.topic(merged.topic)
                                     .partition(p -> p.partitionId(partitionId)
                                                      .partitionOffset(partitionOffset)
                                                      .latestOffset(merged.maximumOffset.value()))
                                     .filters(fs -> merged.filters.forEach(mf -> fs.item(i -> setFetchFilter(i, mf))))
                                     .evaluation(merged.evaluation)
                                     .isolation(i -> i.set(merged.isolation))
                                     .deltaType(t -> t.set(merged.deltaType)))
                        .build()
                        .sizeof()));
        }

        private void onMergedInitialEnd(
            long traceId)
        {
            if (!KafkaState.replyClosing(state))
            {
                doFetchInitialEndIfNecessary(traceId);
            }
        }

        private void doFetchInitialEndIfNecessary(
            long traceId)
        {
            if (!KafkaState.initialClosed(state))
            {
                doFetchInitialEnd(traceId);
            }
        }

        private void doFetchInitialEnd(
            long traceId)
        {
            state = KafkaState.closedInitial(state);

            doEnd(receiver, merged.routedId, merged.resolvedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, merged.authorization, EMPTY_EXTENSION);
        }

        private void onMergedInitialAbort(
            long traceId)
        {
            if (!KafkaState.replyClosing(state))
            {
                doFetchInitialAbortIfNecessary(traceId);
            }
        }

        private void doFetchInitialAbortIfNecessary(
            long traceId)
        {
            if (!KafkaState.initialClosed(state))
            {
                doFetchInitialAbort(traceId);
            }
        }

        private void doFetchInitialAbort(
            long traceId)
        {
            state = KafkaState.closedInitial(state);

            doAbort(receiver, merged.routedId, merged.resolvedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, merged.authorization, EMPTY_EXTENSION);
        }

        private void onFetchInitialReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            final OctetsFW extension = reset.extension();

            state = KafkaState.closedInitial(state);

            final KafkaResetExFW kafkaResetEx = extension.get(kafkaResetExRO::tryWrap);
            final int defaultError = KafkaState.replyClosed(state) ? ERROR_NOT_LEADER_FOR_PARTITION : -1;
            final int error = kafkaResetEx != null ? kafkaResetEx.error() : defaultError;

            doFetchReplyResetIfNecessary(traceId);

            assert KafkaState.closed(state);

            merged.onFetchPartitionLeaderError(traceId, partitionId, error);
        }

        private void onFetchInitialWindow(
            WindowFW window)
        {
            if (!KafkaState.initialOpened(state))
            {
                final long traceId = window.traceId();

                state = KafkaState.openedInitial(state);

                merged.doMergedInitialWindow(traceId, 0L);
            }
        }

        private void onFetchReply(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onFetchReplyBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onFetchReplyData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onFetchReplyEnd(end);
                break;
            case FlushFW.TYPE_ID:
                final FlushFW flush = flushRO.wrap(buffer, index, index + length);
                onFetchReplyFlush(flush);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onFetchReplyAbort(abort);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onFetchInitialReset(reset);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onFetchInitialWindow(window);
                break;
            default:
                break;
            }
        }

        private void onFetchReplyBegin(
            BeginFW begin)
        {
            state = KafkaState.openingReply(state);

            final long traceId = begin.traceId();

            final OctetsFW extension = begin.extension();
            final ExtensionFW beginEx = extensionRO.tryWrap(extension.buffer(), extension.offset(), extension.limit());
            final KafkaBeginExFW kafkaBeginEx = beginEx != null && beginEx.typeId() == kafkaTypeId ?
                kafkaBeginExRO.tryWrap(extension.buffer(), extension.offset(), extension.limit()) : null;

            assert kafkaBeginEx != null;

            final KafkaOffsetFW partition = kafkaBeginEx.fetch().partition();
            final long latestOffset = partition.latestOffset();
            final long stableOffset = partition.stableOffset();

            merged.onFetchPartitionLeaderReady(traceId, partitionId, stableOffset, latestOffset);

            doFetchReplyWindowIfNecessary(traceId);
        }

        private void onFetchReplyData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long budgetId = data.budgetId();
            final int reserved = data.reserved();

            assert budgetId == merged.mergedReplyBudgetId;

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence + reserved;

            assert replyAck <= replySeq;

            if (replySeq > replyAck + replyMax)
            {
                merged.doMergedCleanup(traceId);
            }
            else
            {
                final int flags = data.flags();
                final OctetsFW payload = data.payload();
                final OctetsFW extension = data.extension();
                final KafkaDataExFW kafkaDataEx = extension.get(kafkaDataExRO::tryWrap);

                merged.doMergedReplyData(traceId, flags, reserved, payload, kafkaDataEx);
            }
        }

        private void onFetchReplyEnd(
            EndFW end)
        {
            final long traceId = end.traceId();

            state = KafkaState.closedReply(state);

            if (merged.maximumOffset != HISTORICAL)
            {
                merged.doMergedReplyEndIfNecessary(traceId);
            }
            doFetchInitialEndIfNecessary(traceId);

            merged.onFetchPartitionLeaderError(traceId, partitionId, ERROR_NOT_LEADER_FOR_PARTITION);

            if (merged.maximumOffset == HISTORICAL && merged.fetchStreams.isEmpty())
            {
                merged.doMergedReplyEndIfNecessary(traceId);
            }
        }

        private void onFetchReplyFlush(
                FlushFW flush)
        {
            final long sequence = flush.sequence();
            final long acknowledge = flush.acknowledge();
            final long traceId = flush.traceId();
            final int reserved = flush.reserved();

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence + reserved;

            assert replyAck <= replySeq;

            if (replySeq > replyAck + replyMax)
            {
                merged.doMergedCleanup(traceId);
            }
            else
            {
                final OctetsFW extension = flush.extension();
                final ExtensionFW flushEx = extension.get(extensionRO::tryWrap);

                final KafkaFlushExFW kafkaFlushEx = flushEx != null && flushEx.typeId() == kafkaTypeId ?
                        extension.get(kafkaFlushExRO::tryWrap) : null;

                merged.doMergedReplyFlush(traceId, reserved, kafkaFlushEx);
            }



        }

        private void onFetchReplyAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();

            state = KafkaState.closedReply(state);

            merged.doMergedReplyAbortIfNecessary(traceId);
            doFetchInitialAbortIfNecessary(traceId);

            merged.onFetchPartitionLeaderError(traceId, partitionId, ERROR_NOT_LEADER_FOR_PARTITION);
        }

        private void doFetchReplyWindowIfNecessary(
            long traceId)
        {
            if (KafkaState.replyOpened(merged.state) &&
                KafkaState.replyOpening(state) &&
                !KafkaState.replyClosing(state))
            {
                final int minReplyMax = merged.replyMax;
                final int minReplyNoAck = (int)(merged.replySeq - merged.replyAck);
                final long newReplyAck = Math.max(replySeq - minReplyNoAck, replyAck);

                if (newReplyAck > replyAck || minReplyMax > replyMax || !KafkaState.replyOpened(state))
                {
                    replyAck = newReplyAck;
                    assert replyAck <= replySeq;

                    replyMax = minReplyMax;

                    state = KafkaState.openedReply(state);

                    doWindow(receiver, merged.routedId, merged.resolvedId, replyId, replySeq, replyAck, replyMax,
                            traceId, merged.authorization, merged.mergedReplyBudgetId, merged.replyPad, merged.replyMin);
                }
            }
        }

        private void onMergedReplyReset(
            long traceId)
        {
            if (!KafkaState.initialClosing(state))
            {
                doFetchReplyResetIfNecessary(traceId);
            }
        }

        private void doFetchReplyResetIfNecessary(
            long traceId)
        {
            if (!KafkaState.replyClosed(state))
            {
                doFetchReplyReset(traceId);
            }
        }

        private void doFetchReplyReset(
            long traceId)
        {
            state = KafkaState.closedReply(state);

            doReset(receiver, merged.routedId, merged.resolvedId, replyId, replySeq, replyAck, replyMax,
                    traceId, merged.authorization);
        }

        private void setFetchFilter(
            KafkaFilterFW.Builder builder,
            KafkaMergedFilter filter)
        {
            filter.conditions.forEach(c -> builder.conditionsItem(ci -> c.set(ci)));
        }
    }

    private final class KafkaUnmergedProduceStream
    {
        private final int partitionId;
        private final KafkaMergedStream merged;

        private int leaderId;

        private long initialId;
        private long replyId;
        private MessageConsumer receiver = NO_RECEIVER;

        private int state;

        private long initialBudgetId;
        private long initialSeq;
        private long initialAck;
        private int initialMax;
        private int initialPad;

        private long replySeq;
        private long replyAck;
        private int replyMax;

        private KafkaUnmergedProduceStream(
            int partitionId,
            int leaderId,
            KafkaMergedStream merged)
        {
            this.partitionId = partitionId;
            this.leaderId = leaderId;
            this.merged = merged;
        }

        private int initialNoAck()
        {
            return (int)(initialSeq - initialAck);
        }

        private void doProduceInitialBeginIfNecessary(
            long traceId)
        {
            if (!KafkaState.initialOpening(state))
            {
                doProduceInitialBegin(traceId);
            }
        }

        private void doProduceInitialBegin(
            long traceId)
        {
            if (KafkaState.closed(state))
            {
                state = 0;
            }

            assert state == 0;

            state = KafkaState.openingInitial(state);

            this.initialId = supplyInitialId.applyAsLong(merged.resolvedId);
            this.replyId = supplyReplyId.applyAsLong(initialId);
            this.receiver = newStream(this::onProduceReply,
                merged.routedId, merged.resolvedId, initialId, initialSeq, initialAck, initialMax,
                traceId, merged.authorization, leaderId,
                ex -> ex.set((b, o, l) -> kafkaBeginExRW.wrap(b, o, l)
                        .typeId(kafkaTypeId)
                        .produce(pr -> pr
                            .transaction((String) null) // TODO: default in kafka.idl
                            .topic(merged.topic)
                            .partition(part -> part
                                .partitionId(partitionId)
                                .partitionOffset(HISTORICAL.value())))
                        .build()
                        .sizeof()));
        }

        private void doProduceInitialData(
            long traceId,
            int reserved,
            int flags,
            long budgetId,
            OctetsFW payload,
            OctetsFW extension)
        {
            Flyweight newKafkaDataEx = EMPTY_OCTETS;

            final ExtensionFW dataEx = extension.get(extensionRO::tryWrap);
            if (flags != FLAGS_NONE && flags != FLAGS_INCOMPLETE && dataEx != null)
            {
                final KafkaDataExFW kafkaDataEx = dataEx.typeId() == kafkaTypeId ?
                        extension.get(kafkaDataExRO::tryWrap) : null;

                assert kafkaDataEx != null;
                assert kafkaDataEx.kind() == KafkaFlushExFW.KIND_MERGED;
                final KafkaMergedDataExFW kafkaMergedDataEx = kafkaDataEx.merged();
                final int deferred = kafkaMergedDataEx.deferred();
                final long timestamp = kafkaMergedDataEx.timestamp();
                final KafkaOffsetFW partition = kafkaMergedDataEx.partition();
                final KafkaKeyFW key = kafkaMergedDataEx.key();
                final Array32FW<KafkaHeaderFW> headers = kafkaMergedDataEx.headers();

                final int partitionId = partition.partitionId();
                assert partitionId == DYNAMIC_PARTITION || partitionId == this.partitionId;
                final int sequence = (int) partition.partitionOffset();
                final KafkaAckMode ackMode = merged.ackMode;

                switch (flags)
                {
                case FLAGS_INIT_AND_FIN:
                case FLAGS_INIT:
                    newKafkaDataEx = kafkaDataExRW.wrap(extBuffer, 0, extBuffer.capacity())
                            .typeId(kafkaTypeId)
                            .produce(pr -> pr
                                .deferred(deferred)
                                .timestamp(timestamp)
                                .sequence(sequence)
                                .ackMode(a -> a.set(ackMode))
                                .key(k -> k
                                    .length(key.length())
                                    .value(key.value()))
                                .headers(hs -> headers.forEach(h -> hs
                                    .item(i -> i
                                        .nameLen(h.nameLen())
                                        .name(h.name())
                                        .valueLen(h.valueLen())
                                        .value(h.value())))))
                            .build();
                    break;
                case FLAGS_FIN:
                    newKafkaDataEx = kafkaDataExRW.wrap(extBuffer, 0, extBuffer.capacity())
                            .typeId(kafkaTypeId)
                            .produce(pr -> pr.headers(hs -> headers.forEach(h -> hs.item(i -> i.nameLen(h.nameLen())
                                                                                             .name(h.name())
                                                                                             .valueLen(h.valueLen())
                                                                                             .value(h.value())))))
                            .build();
                    break;
                }
            }

            doData(receiver, merged.routedId, merged.resolvedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, merged.authorization, budgetId, reserved, flags, payload, newKafkaDataEx);

            initialSeq += reserved;

            assert initialAck <= initialSeq;
        }

        private void doProduceInitialEndIfNecessary(
            long traceId)
        {
            if (!KafkaState.initialClosed(state))
            {
                doProduceInitialEnd(traceId);
            }
        }

        private void doProduceInitialEnd(
            long traceId)
        {
            state = KafkaState.closedInitial(state);

            doEnd(receiver, merged.routedId, merged.resolvedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, merged.authorization, EMPTY_EXTENSION);
        }

        private void doProduceInitialAbortIfNecessary(
            long traceId)
        {
            if (!KafkaState.initialClosed(state))
            {
                doProduceInitialAbort(traceId);
            }
        }

        private void doProduceInitialAbort(
            long traceId)
        {
            state = KafkaState.closedInitial(state);

            doAbort(receiver, merged.routedId, merged.resolvedId, initialId, initialSeq, initialAck, initialMax,
                    traceId, merged.authorization, EMPTY_EXTENSION);
        }

        private void onProduceInitialReset(
            ResetFW reset)
        {
            final long traceId = reset.traceId();
            final OctetsFW extension = reset.extension();

            state = KafkaState.closedInitial(state);

            final KafkaResetExFW kafkaResetEx = extension.get(kafkaResetExRO::tryWrap);
            final int error = kafkaResetEx != null ? kafkaResetEx.error() : ERROR_UNKNOWN;

            doProduceReplyResetIfNecessary(traceId);

            assert KafkaState.closed(state);

            merged.onProducePartitionLeaderError(traceId, partitionId, error);
        }

        private void onProduceInitialWindow(
            WindowFW window)
        {
            final long sequence = window.sequence();
            final long acknowledge = window.acknowledge();
            final int maximum = window.maximum();
            final long traceId = window.traceId();
            final long budgetId = window.budgetId();
            final int padding = window.padding();

            assert acknowledge <= sequence;
            assert sequence <= initialSeq;
            assert acknowledge >= initialAck;
            assert maximum + acknowledge >= initialMax + initialAck;

            this.initialAck = acknowledge;
            this.initialMax = maximum;
            this.initialPad = padding;
            this.initialBudgetId = budgetId;

            assert initialAck <= initialSeq;

            state = KafkaState.openedInitial(state);

            merged.doMergedInitialWindow(traceId, initialBudgetId);
        }

        private void onProduceReply(
            int msgTypeId,
            DirectBuffer buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                onProduceReplyBegin(begin);
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                onProduceReplyData(data);
                break;
            case EndFW.TYPE_ID:
                final EndFW end = endRO.wrap(buffer, index, index + length);
                onProduceReplyEnd(end);
                break;
            case AbortFW.TYPE_ID:
                final AbortFW abort = abortRO.wrap(buffer, index, index + length);
                onProduceReplyAbort(abort);
                break;
            case ResetFW.TYPE_ID:
                final ResetFW reset = resetRO.wrap(buffer, index, index + length);
                onProduceInitialReset(reset);
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                onProduceInitialWindow(window);
                break;
            default:
                break;
            }
        }

        private void onProduceReplyBegin(
            BeginFW begin)
        {
            state = KafkaState.openingReply(state);

            final long traceId = begin.traceId();

            merged.onProducePartitionLeaderReady(traceId, partitionId);

            doProduceReplyWindowIfNecessary(traceId);
        }

        private void onProduceReplyData(
            DataFW data)
        {
            final long sequence = data.sequence();
            final long acknowledge = data.acknowledge();
            final long traceId = data.traceId();
            final long budgetId = data.budgetId();
            final int reserved = data.reserved();

            assert budgetId == merged.mergedReplyBudgetId;

            assert acknowledge <= sequence;
            assert sequence >= replySeq;

            replySeq = sequence + reserved;

            assert replyAck <= replySeq;

            if (replySeq > replyAck + replyMax)
            {
                merged.doMergedCleanup(traceId);
            }
            else
            {
                final int flags = data.flags();
                final OctetsFW payload = data.payload();
                final OctetsFW extension = data.extension();
                final KafkaDataExFW kafkaDataEx = extension.get(kafkaDataExRO::tryWrap);

                merged.doMergedReplyData(traceId, flags, reserved, payload, kafkaDataEx);
            }
        }

        private void onProduceReplyEnd(
            EndFW end)
        {
            final long traceId = end.traceId();

            state = KafkaState.closedReply(state);

            merged.doMergedReplyEndIfNecessary(traceId);

            doProduceInitialEndIfNecessary(traceId);
        }

        private void onProduceReplyAbort(
            AbortFW abort)
        {
            final long traceId = abort.traceId();

            state = KafkaState.closedReply(state);

            merged.doMergedReplyAbortIfNecessary(traceId);

            doProduceInitialAbortIfNecessary(traceId);
        }

        private void doProduceReplyWindowIfNecessary(
            long traceId)
        {
            if (KafkaState.replyOpened(merged.state) &&
                KafkaState.replyOpening(state) &&
                !KafkaState.replyClosing(state))
            {
                final int minReplyMax = merged.replyMax;
                final int minReplyNoAck = (int)(merged.replySeq - merged.replyAck);
                final long newReplyAck = Math.max(replySeq - minReplyNoAck, replyAck);

                if (newReplyAck > replyAck || minReplyMax > replyMax || !KafkaState.replyOpened(state))
                {
                    replyAck = newReplyAck;
                    assert replyAck <= replySeq;

                    replyMax = minReplyMax;

                    state = KafkaState.openedReply(state);

                    doWindow(receiver, merged.routedId, merged.resolvedId, replyId, replySeq, replyAck, replyMax,
                            traceId, merged.authorization, merged.mergedReplyBudgetId, merged.replyPad, DEFAULT_MINIMUM);
                }
            }
        }

        private void doProduceReplyResetIfNecessary(
            long traceId)
        {
            if (!KafkaState.replyClosed(state))
            {
                doProduceReplyReset(traceId);
            }
        }

        private void doProduceReplyReset(
            long traceId)
        {
            state = KafkaState.closedReply(state);

            doReset(receiver, merged.routedId, merged.resolvedId, replyId, replySeq, replyAck, replyMax,
                    traceId, merged.authorization);
        }
    }
}
