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
package io.aklivity.zilla.runtime.binding.kafka.api;

import java.nio.ByteOrder;
import java.util.PrimitiveIterator;

import io.aklivity.zilla.runtime.binding.kafka.internal.types.VarStringFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.Varuint32FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.Varuint32nFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.topic_metadata.BrokerMetadataFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.topic_metadata.MetadataResponsePart2FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.topic_metadata.MetadataResponsePart3FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.topic_metadata.PartitionMetadataFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.topic_metadata.TopicMetadataFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.topic_metadata.TopicMetadataPart2FW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;

/**
 * Hand-crafted (not {@code .idl}-generated) Metadata v9 response cursor. Delegates the actual byte
 * decoding to the generated {@code protocol.idl} wire types, adding the version-tolerant
 * {@link MetadataResponse} view on top - a fixed-default-on-read behavior the flyweight generator
 * cannot produce, since generated builders only default missing fields on write.
 * <p>
 * Also works around a flyweight-maven-plugin code generation gap: the generated {@code int32[]}
 * array-element accessor (used here for {@code replicas}/{@code isr}/{@code offlineReplicas}) reads
 * each element via the buffer's native byte order, ignoring this scope's {@code option byteorder
 * network}, unlike every scalar field accessor in the same generated class. {@link #networkOrder}
 * corrects this per element rather than papering over it silently.
 */
public final class MetadataResponseV9FW implements MetadataResponse
{
    private static final int FIELD_SIZE_THROTTLE_TIME_MILLIS = 4;

    private final Varuint32FW taggedFieldsRO = new Varuint32FW();
    private final Varuint32nFW brokerCountRO = new Varuint32nFW();
    private final BrokerMetadataFW brokerMetadataRO = new BrokerMetadataFW();
    private final MetadataResponsePart2FW metadataResponsePart2RO = new MetadataResponsePart2FW();
    private final TopicMetadataFW topicMetadataRO = new TopicMetadataFW();
    private final PartitionMetadataFW partitionMetadataRO = new PartitionMetadataFW();
    private final TopicMetadataPart2FW topicMetadataPart2RO = new TopicMetadataPart2FW();
    private final MetadataResponsePart3FW metadataResponsePart3RO = new MetadataResponsePart3FW();

    private final BrokerView brokerView = new BrokerView();
    private final TopicView topicView = new TopicView();
    private final PartitionView partitionView = new PartitionView();

    private final NetworkOrderIntIterator replicasFix = new NetworkOrderIntIterator();
    private final NetworkOrderIntIterator isrFix = new NetworkOrderIntIterator();
    private final NetworkOrderIntIterator offlineReplicasFix = new NetworkOrderIntIterator();

    private DirectBufferEx buffer;
    private int progress;
    private int limit;

    private int throttleTimeMillis;
    private int brokerCount;
    private int brokersRemaining;
    private boolean clusterFieldsRead;

    private int brokerNodeId;
    private int brokerHostOffset;
    private int brokerHostLength;
    private int brokerPort;
    private int brokerRackOffset;
    private int brokerRackLength;

    private int clusterIdOffset;
    private int clusterIdLength;
    private int controllerId;
    private int topicCount;
    private int topicsRemaining;
    private int partitionsRemaining;
    private boolean topicOpen;
    private boolean responseClosed;

    private short topicError;
    private int topicNameOffset;
    private int topicNameLength;
    private boolean topicIsInternal;
    private int topicPartitionCount;

    private short partitionError;
    private int partitionId;
    private int partitionLeader;
    private int partitionLeaderEpoch;
    private int partitionReplicaCount;
    private PrimitiveIterator.OfInt partitionReplicas;
    private int partitionIsrCount;
    private PrimitiveIterator.OfInt partitionIsr;
    private int partitionOfflineReplicaCount;
    private PrimitiveIterator.OfInt partitionOfflineReplicas;

    /**
     * Wraps a complete Metadata v9 response body: tagged fields, throttle time, and broker count,
     * followed by the brokers themselves. The leading {@code correlationId} is assumed already
     * consumed by the caller, as with {@link CreateTopicsResponseV7FW}.
     */
    public MetadataResponseV9FW wrap(
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        int progress = offset;

        final Varuint32FW taggedFields = taggedFieldsRO.wrap(buffer, progress, limit);
        progress = taggedFields.limit();

        final int throttleTimeMillis = buffer.getInt(progress, ByteOrder.BIG_ENDIAN);
        progress += FIELD_SIZE_THROTTLE_TIME_MILLIS;

        final Varuint32nFW brokerCount = brokerCountRO.wrap(buffer, progress, limit);
        progress = brokerCount.limit();

        this.buffer = buffer;
        this.limit = limit;
        this.progress = progress;
        this.throttleTimeMillis = throttleTimeMillis;
        this.brokerCount = Math.max(brokerCount.value(), 0);
        this.brokersRemaining = this.brokerCount;
        this.clusterFieldsRead = false;
        this.topicsRemaining = 0;
        this.partitionsRemaining = 0;
        this.topicOpen = false;
        this.responseClosed = false;

        return this;
    }

    /**
     * @return the offset just past the last byte consumed so far; final once {@link #hasNext()} returns false
     */
    public int limit()
    {
        return progress;
    }

    @Override
    public DirectBufferEx buffer()
    {
        return buffer;
    }

    @Override
    public int throttleTimeMillis()
    {
        return throttleTimeMillis;
    }

    @Override
    public int brokerCount()
    {
        return brokerCount;
    }

    @Override
    public boolean hasNextBroker()
    {
        if (brokersRemaining == 0 && !clusterFieldsRead)
        {
            final MetadataResponsePart2FW part2 = metadataResponsePart2RO.wrap(buffer, progress, limit);
            progress = part2.limit();

            final VarStringFW clusterId = part2.clusterId();
            this.clusterIdOffset = clusterId.length() < 0 ? -1 : clusterId.offset() + clusterId.fieldSizeLength();
            this.clusterIdLength = clusterId.length();
            this.controllerId = part2.controllerId();
            this.topicCount = Math.max(part2.topicCount(), 0);
            this.topicsRemaining = this.topicCount;
            this.clusterFieldsRead = true;
        }

        return brokersRemaining != 0;
    }

    @Override
    public Broker nextBroker()
    {
        brokersRemaining--;

        final BrokerMetadataFW broker = brokerMetadataRO.wrap(buffer, progress, limit);
        progress = broker.limit();

        final VarStringFW host = broker.host();
        final VarStringFW rack = broker.rack();

        this.brokerNodeId = broker.nodeId();
        this.brokerHostOffset = host.offset() + host.fieldSizeLength();
        this.brokerHostLength = host.length();
        this.brokerPort = broker.port();
        this.brokerRackOffset = rack.length() < 0 ? -1 : rack.offset() + rack.fieldSizeLength();
        this.brokerRackLength = rack.length();

        return brokerView;
    }

    @Override
    public int clusterIdOffset()
    {
        return clusterIdOffset;
    }

    @Override
    public int clusterIdLength()
    {
        return clusterIdLength;
    }

    @Override
    public int controllerId()
    {
        return controllerId;
    }

    @Override
    public int topicCount()
    {
        return topicCount;
    }

    @Override
    public boolean hasNext()
    {
        // hasNext() consumes the per-topic and overall trailing tagged fields as the cursor moves
        // past the last partition or topic that reaches them, so next() never needs to look ahead.
        if (partitionsRemaining == 0 && topicOpen)
        {
            final TopicMetadataPart2FW topicPart2 = topicMetadataPart2RO.wrap(buffer, progress, limit);
            progress = topicPart2.limit();
            topicOpen = false;
        }

        if (topicsRemaining == 0 && partitionsRemaining == 0 && !responseClosed)
        {
            final MetadataResponsePart3FW responsePart3 = metadataResponsePart3RO.wrap(buffer, progress, limit);
            progress = responsePart3.limit();
            responseClosed = true;
        }

        return topicsRemaining != 0 || partitionsRemaining != 0;
    }

    @Override
    public Kind next()
    {
        final Kind kind;

        if (partitionsRemaining != 0)
        {
            partitionsRemaining--;

            final PartitionMetadataFW partition = partitionMetadataRO.wrap(buffer, progress, limit);
            progress = partition.limit();

            this.partitionError = partition.errorCode();
            this.partitionId = partition.partitionId();
            this.partitionLeader = partition.leader();
            this.partitionLeaderEpoch = partition.leaderEpoch();
            this.partitionReplicaCount = Math.max(partition.replicaCount(), 0);
            this.partitionReplicas = replicasFix.wrap(partition.replicas());
            this.partitionIsrCount = Math.max(partition.isrCount(), 0);
            this.partitionIsr = isrFix.wrap(partition.isr());
            this.partitionOfflineReplicaCount = Math.max(partition.offlineReplicaCount(), 0);
            this.partitionOfflineReplicas = offlineReplicasFix.wrap(partition.offlineReplicas());

            kind = Kind.PARTITION;
        }
        else
        {
            topicsRemaining--;

            final TopicMetadataFW topic = topicMetadataRO.wrap(buffer, progress, limit);
            progress = topic.limit();

            final VarStringFW name = topic.topic();

            this.topicError = topic.errorCode();
            this.topicNameOffset = name.offset() + name.fieldSizeLength();
            this.topicNameLength = name.length();
            this.topicIsInternal = topic.isInternal() != 0;
            this.topicPartitionCount = Math.max(topic.partitionCount(), 0);

            this.partitionsRemaining = topicPartitionCount;
            this.topicOpen = true;

            kind = Kind.TOPIC;
        }

        return kind;
    }

    @Override
    public Topic topic()
    {
        return topicView;
    }

    @Override
    public Partition partition()
    {
        return partitionView;
    }

    private final class BrokerView implements Broker
    {
        @Override
        public int nodeId()
        {
            return brokerNodeId;
        }

        @Override
        public int hostOffset()
        {
            return brokerHostOffset;
        }

        @Override
        public int hostLength()
        {
            return brokerHostLength;
        }

        @Override
        public int port()
        {
            return brokerPort;
        }

        @Override
        public int rackOffset()
        {
            return brokerRackOffset;
        }

        @Override
        public int rackLength()
        {
            return brokerRackLength;
        }
    }

    private final class TopicView implements Topic
    {
        @Override
        public short error()
        {
            return topicError;
        }

        @Override
        public int nameOffset()
        {
            return topicNameOffset;
        }

        @Override
        public int nameLength()
        {
            return topicNameLength;
        }

        @Override
        public boolean isInternal()
        {
            return topicIsInternal;
        }

        @Override
        public int partitionCount()
        {
            return topicPartitionCount;
        }
    }

    private final class PartitionView implements Partition
    {
        @Override
        public short error()
        {
            return partitionError;
        }

        @Override
        public int partitionId()
        {
            return partitionId;
        }

        @Override
        public int leader()
        {
            return partitionLeader;
        }

        @Override
        public int leaderEpoch()
        {
            return partitionLeaderEpoch;
        }

        @Override
        public int replicaCount()
        {
            return partitionReplicaCount;
        }

        @Override
        public PrimitiveIterator.OfInt replicas()
        {
            return partitionReplicas;
        }

        @Override
        public int isrCount()
        {
            return partitionIsrCount;
        }

        @Override
        public PrimitiveIterator.OfInt isr()
        {
            return partitionIsr;
        }

        @Override
        public int offlineReplicaCount()
        {
            return partitionOfflineReplicaCount;
        }

        @Override
        public PrimitiveIterator.OfInt offlineReplicas()
        {
            return partitionOfflineReplicas;
        }
    }

    /**
     * Corrects each element of a generated {@code int32[]} array accessor to network (big-endian)
     * byte order - see the class-level note on the flyweight-maven-plugin generation gap this works
     * around. A {@code null} delegate (the generator's own null-array convention) iterates as empty.
     */
    private static final class NetworkOrderIntIterator implements PrimitiveIterator.OfInt
    {
        private PrimitiveIterator.OfInt delegate;

        private NetworkOrderIntIterator wrap(
            PrimitiveIterator.OfInt delegate)
        {
            this.delegate = delegate;
            return this;
        }

        @Override
        public boolean hasNext()
        {
            return delegate != null && delegate.hasNext();
        }

        @Override
        public int nextInt()
        {
            final int value = delegate.nextInt();
            return ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN ? value : Integer.reverseBytes(value);
        }
    }
}
