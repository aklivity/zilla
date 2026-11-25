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
package io.aklivity.zilla.runtime.binding.kafka.internal.bench;

import java.util.ArrayList;
import java.util.List;

import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.LongArrayQueue;

import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaBeginExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaDataExFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.stream.KafkaFlushExFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.binding.StreamState;
import io.aklivity.zilla.runtime.engine.binding.function.MessageConsumer;
import io.aklivity.zilla.runtime.engine.internal.types.OctetsFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.DataFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.FlushFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.ResetFW;
import io.aklivity.zilla.runtime.engine.internal.types.stream.WindowFW;
import io.aklivity.zilla.specs.binding.kafka.internal.KafkaFunctions;

/**
 * Stands in for the Kafka client binding below a {@code cache_server}: the stream-level counterpart of
 * {@link KafkaBrokerStub}. It answers the topic meta with the configured partitions, accepts the produce
 * streams, and, once {@code delayNanos} has elapsed, acknowledges each produced record and feeds it back on the
 * partition's fetch stream, the way a broker's committed log is read back to populate the cache.
 * <p>
 * Frames are only produced by {@link #drain()}, which the benchmark thread calls, so the delay is observable and a
 * request is never answered from inside the frame that carried it. The per-record extensions are patched in place
 * from templates so the stub itself allocates nothing per record.
 */
final class KafkaCacheExitStub implements BindingHandler
{
    private static final int WINDOW = 1024 * 1024;
    private static final int FLAGS_INIT_FIN = 0x03;
    private static final long TIMESTAMP = 1_700_000_000_000L;

    private final BeginFW beginRO = new BeginFW();
    private final DataFW dataRO = new DataFW();
    private final WindowFW windowRO = new WindowFW();
    private final KafkaBeginExFW beginExRO = new KafkaBeginExFW();
    private final BeginFW.Builder beginRW = new BeginFW.Builder();
    private final DataFW.Builder dataRW = new DataFW.Builder();
    private final FlushFW.Builder flushRW = new FlushFW.Builder();
    private final WindowFW.Builder windowRW = new WindowFW.Builder();
    private final MutableDirectBufferEx writeBuffer = new UnsafeBufferEx(new byte[64 * 1024]);
    private final Int2ObjectHashMap<PartitionLog> partitions = new Int2ObjectHashMap<>();
    private final List<StubStream> streams = new ArrayList<>();
    private final long delayNanos;
    private final int partitionCount;
    private final int kafkaTypeId;
    private final DirectBufferEx payload;
    private final UnsafeBufferEx describeDataEx;
    private final UnsafeBufferEx metaBeginEx;
    private final UnsafeBufferEx metaDataEx;
    private final UnsafeBufferEx produceBeginEx;
    private final UnsafeBufferEx fetchBeginEx;
    private final UnsafeBufferEx fetchDataEx;
    private final UnsafeBufferEx produceAckEx;
    private final List<FetchStream> heldFetches = new ArrayList<>();
    private boolean fetchReplyHeld;
    private int fetchBegins;
    private final int fetchPartitionOffset;
    private final int ackPartitionOffset;

    private long produced;
    private long acknowledged;
    private long populated;
    private int unknown;

    KafkaCacheExitStub(
        int kafkaTypeId,
        int partitionCount,
        long delayNanos,
        DirectBufferEx payload)
    {
        this.kafkaTypeId = kafkaTypeId;
        this.partitionCount = partitionCount;
        this.delayNanos = delayNanos;
        this.payload = payload;

        final KafkaFunctions.KafkaDataExBuilder.KafkaMetaDataExBuilder meta = KafkaFunctions.dataEx()
            .typeId(kafkaTypeId)
            .meta();
        for (int partitionId = 0; partitionId < partitionCount; partitionId++)
        {
            meta.partition(partitionId, 0);
            partitions.put(partitionId, new PartitionLog());
        }
        this.metaDataEx = new UnsafeBufferEx(meta.build().build());

        this.describeDataEx = new UnsafeBufferEx(KafkaFunctions.dataEx()
            .typeId(kafkaTypeId)
            .describe()
                .config("cleanup.policy", "delete")
                .config("max.message.bytes", "1048588")
                .config("segment.bytes", "2147483647")
                .config("segment.index.bytes", "104857600")
                .config("segment.ms", "604800000")
                .config("retention.bytes", "-1")
                .config("retention.ms", "604800000")
                .config("delete.retention.ms", "86400000")
                .config("min.compaction.lag.ms", "0")
                .build()
            .build());

        this.metaBeginEx = new UnsafeBufferEx(KafkaFunctions.beginEx()
            .typeId(kafkaTypeId)
            .meta()
                .topic("test")
                .build()
            .build());
        this.produceBeginEx = new UnsafeBufferEx(KafkaFunctions.beginEx()
            .typeId(kafkaTypeId)
            .produce()
                .topic("test")
                .partition(0)
                .build()
            .build());
        this.fetchBeginEx = new UnsafeBufferEx(KafkaFunctions.beginEx()
            .typeId(kafkaTypeId)
            .fetch()
                .topic("test")
                .partition(0, 0, 0)
                .build()
            .build());
        this.fetchDataEx = new UnsafeBufferEx(KafkaFunctions.dataEx()
            .typeId(kafkaTypeId)
            .fetch()
                .timestamp(TIMESTAMP)
                .partition(0, 0, 0)
                .build()
            .build());
        this.produceAckEx = new UnsafeBufferEx(KafkaFunctions.flushEx()
            .typeId(kafkaTypeId)
            .produce()
                .partition(0, 0)
                .timestamp(TIMESTAMP)
                .build()
            .build());

        this.fetchPartitionOffset = new KafkaDataExFW().wrap(fetchDataEx, 0, fetchDataEx.capacity())
            .fetch().partition().offset();
        this.ackPartitionOffset = new KafkaFlushExFW().wrap(produceAckEx, 0, produceAckEx.capacity())
            .produce().partition().offset();
    }

    void holdFetchReply()
    {
        fetchReplyHeld = true;
    }

    void releaseFetchReply()
    {
        fetchReplyHeld = false;
        heldFetches.forEach(FetchStream::doReplyFetchBegin);
        heldFetches.clear();
    }

    int fetchBegins()
    {
        return fetchBegins;
    }

    long produced()
    {
        return produced;
    }

    long acknowledged()
    {
        return acknowledged;
    }

    long populated()
    {
        return populated;
    }

    int unknown()
    {
        return unknown;
    }

    int drain()
    {
        final long now = System.nanoTime();

        int work = 0;
        for (int index = 0; index < streams.size(); index++)
        {
            work += streams.get(index).drain(now);
        }

        return work;
    }

    @Override
    public MessageConsumer newStream(
        int msgTypeId,
        DirectBufferEx buffer,
        int index,
        int length,
        MessageConsumer sender)
    {
        final BeginFW begin = beginRO.wrap(buffer, index, index + length);
        final KafkaBeginExFW beginEx = beginExRO.wrap(begin.extension().buffer(), begin.extension().offset(),
            begin.extension().limit());

        final StubStream stream;
        switch (beginEx.kind())
        {
        case KafkaBeginExFW.KIND_META:
            stream = new MetaStream(sender);
            break;
        case KafkaBeginExFW.KIND_PRODUCE:
            stream = new ProduceStream(sender, beginEx.produce().partition().partitionId());
            break;
        case KafkaBeginExFW.KIND_DESCRIBE:
            stream = new DescribeStream(sender, new UnsafeBufferEx(copyOf(begin.extension())));
            break;
        case KafkaBeginExFW.KIND_FETCH:
            stream = new FetchStream(sender, beginEx.fetch().partition().partitionId());
            break;
        default:
            unknown++;
            stream = new StubStream(sender);
            break;
        }

        streams.add(stream);
        return stream::onMessage;
    }

    private static byte[] copyOf(
        OctetsFW octets)
    {
        final byte[] copy = new byte[octets.sizeof()];
        octets.buffer().getBytes(octets.offset(), copy);
        return copy;
    }

    private static final class PartitionLog
    {
        private final LongArrayQueue dueNanos = new LongArrayQueue();
        private final LongArrayQueue offsets = new LongArrayQueue();
        private final LongArrayQueue unfetched = new LongArrayQueue();

        private long nextOffset;
        private FetchStream fetch;
    }

    private class StubStream
    {
        final MessageConsumer sender;

        int state;
        long originId;
        long routedId;
        long initialId;
        long replyId;
        long initialSeq;
        long replySeq;
        long replyAck;
        int replyMax;
        int replyPad;

        StubStream(
            MessageConsumer sender)
        {
            this.sender = sender;
        }

        int drain(
            long now)
        {
            return 0;
        }

        final void onMessage(
            int msgTypeId,
            DirectBufferEx buffer,
            int index,
            int length)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                final BeginFW begin = beginRO.wrap(buffer, index, index + length);
                originId = begin.originId();
                routedId = begin.routedId();
                initialId = begin.streamId();
                replyId = initialId & ~1L;
                state = StreamState.openInitial(state);
                onBegin();
                break;
            case DataFW.TYPE_ID:
                final DataFW data = dataRO.wrap(buffer, index, index + length);
                initialSeq += data.reserved();
                onData();
                doInitialWindow();
                break;
            case WindowFW.TYPE_ID:
                final WindowFW window = windowRO.wrap(buffer, index, index + length);
                replyAck = window.acknowledge();
                replyMax = window.maximum();
                replyPad = window.padding();
                onWindow();
                break;
            case EndFW.TYPE_ID:
            case AbortFW.TYPE_ID:
            case ResetFW.TYPE_ID:
                state = StreamState.closeReply(StreamState.closeInitial(state));
                break;
            default:
                break;
            }
        }

        void onBegin()
        {
        }

        void onData()
        {
        }

        void onWindow()
        {
        }

        final void doInitialWindow()
        {
            final WindowFW window = windowRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(routedId)
                .routedId(originId)
                .streamId(initialId)
                .sequence(initialSeq)
                .acknowledge(initialSeq)
                .state(state)
                .maximum(WINDOW)
                .budgetId(0L)
                .padding(0)
                .build();

            sender.accept(window.typeId(), window.buffer(), window.offset(), window.sizeof());
        }

        final void doReplyBegin(
            DirectBufferEx extension)
        {
            state = StreamState.openingReply(state);

            final BeginFW begin = beginRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(routedId)
                .routedId(originId)
                .streamId(replyId)
                .sequence(replySeq)
                .acknowledge(replyAck)
                .state(state)
                .maximum(0)
                .affinity(0L)
                .extension(extension, 0, extension.capacity())
                .build();

            sender.accept(begin.typeId(), begin.buffer(), begin.offset(), begin.sizeof());
        }

        final void doReplyData(
            DirectBufferEx payload,
            int length,
            DirectBufferEx extension)
        {
            final DataFW data = dataRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(routedId)
                .routedId(originId)
                .streamId(replyId)
                .sequence(replySeq)
                .acknowledge(replyAck)
                .state(state)
                .maximum(0)
                .flags(FLAGS_INIT_FIN)
                .budgetId(0L)
                .reserved(length + replyPad)
                .payload(payload, 0, length)
                .extension(extension, 0, extension.capacity())
                .build();

            replySeq += data.reserved();

            sender.accept(data.typeId(), data.buffer(), data.offset(), data.sizeof());
        }

        final void doReplyFlush(
            DirectBufferEx extension)
        {
            final FlushFW flush = flushRW.wrap(writeBuffer, 0, writeBuffer.capacity())
                .originId(routedId)
                .routedId(originId)
                .streamId(replyId)
                .sequence(replySeq)
                .acknowledge(replyAck)
                .state(state)
                .maximum(0)
                .budgetId(0L)
                .extension(extension, 0, extension.capacity())
                .build();

            sender.accept(flush.typeId(), flush.buffer(), flush.offset(), flush.sizeof());
        }
    }

    private final class MetaStream extends StubStream
    {
        MetaStream(
            MessageConsumer sender)
        {
            super(sender);
        }

        @Override
        void onBegin()
        {
            doInitialWindow();
            doReplyBegin(metaBeginEx);
            doReplyData(payload, 0, metaDataEx);
        }
    }

    private final class DescribeStream extends StubStream
    {
        private final DirectBufferEx beginEx;

        DescribeStream(
            MessageConsumer sender,
            DirectBufferEx beginEx)
        {
            super(sender);
            this.beginEx = beginEx;
        }

        @Override
        void onBegin()
        {
            doInitialWindow();
            doReplyBegin(beginEx);
            doReplyData(payload, 0, describeDataEx);
        }
    }

    private final class ProduceStream extends StubStream
    {
        private final PartitionLog log;
        private final int partitionId;

        ProduceStream(
            MessageConsumer sender,
            int partitionId)
        {
            super(sender);
            this.partitionId = partitionId;
            this.log = partitions.get(partitionId);
        }

        @Override
        void onBegin()
        {
            doInitialWindow();
            doReplyBegin(produceBeginEx);
        }

        @Override
        void onData()
        {
            final long offset = log.nextOffset++;
            log.dueNanos.addLong(System.nanoTime() + delayNanos);
            log.offsets.addLong(offset);
            produced++;
        }

        @Override
        int drain(
            long now)
        {
            int work = 0;

            while (!log.dueNanos.isEmpty() && log.dueNanos.peekLong() <= now)
            {
                log.dueNanos.removeLong();
                final long offset = log.offsets.removeLong();

                produceAckEx.putInt(ackPartitionOffset, partitionId);
                produceAckEx.putLong(ackPartitionOffset + Integer.BYTES, offset);
                doReplyFlush(produceAckEx);
                acknowledged++;

                log.unfetched.addLong(offset);
                work++;
            }

            return work;
        }
    }

    private final class FetchStream extends StubStream
    {
        private final PartitionLog log;
        private final int partitionId;

        private long deliveredHere;
        private long populatedHere;

        FetchStream(
            MessageConsumer sender,
            int partitionId)
        {
            super(sender);
            this.partitionId = partitionId;
            this.log = partitions.get(partitionId);
        }

        @Override
        void onBegin()
        {
            log.fetch = this;
            fetchBegins++;
            doInitialWindow();

            if (fetchReplyHeld)
            {
                heldFetches.add(this);
            }
            else
            {
                doReplyFetchBegin();
            }
        }

        private void doReplyFetchBegin()
        {
            doReplyBegin(fetchBeginEx);
        }

        @Override
        void onWindow()
        {
            if (replyAck >= replySeq)
            {
                populated += deliveredHere - populatedHere;
                populatedHere = deliveredHere;
            }
        }

        @Override
        int drain(
            long now)
        {
            int work = 0;

            while (!log.unfetched.isEmpty() &&
                   replyAck + replyMax - replySeq >= payload.capacity() + replyPad)
            {
                final long offset = log.unfetched.removeLong();

                fetchDataEx.putInt(fetchPartitionOffset, partitionId);
                fetchDataEx.putLong(fetchPartitionOffset + Integer.BYTES, offset);
                fetchDataEx.putLong(fetchPartitionOffset + Integer.BYTES + Long.BYTES, offset);
                fetchDataEx.putLong(fetchPartitionOffset + Integer.BYTES + 2 * Long.BYTES, offset);
                doReplyData(payload, payload.capacity(), fetchDataEx);
                deliveredHere++;
                work++;
            }

            return work;
        }
    }
}
