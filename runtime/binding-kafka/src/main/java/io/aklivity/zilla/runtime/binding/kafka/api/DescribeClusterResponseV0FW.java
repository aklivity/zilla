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

import io.aklivity.zilla.runtime.binding.kafka.internal.types.VarStringFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.Varuint32FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.Varuint32nFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.describe_cluster.ClusterBrokerFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.describe_cluster.DescribeClusterResponsePart2FW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;

/**
 * Hand-crafted (not {@code .idl}-generated) DescribeCluster v0 response cursor. Delegates the actual
 * byte decoding to the generated {@code protocol.idl} wire types, adding the version-tolerant
 * {@link DescribeClusterResponse} view on top - a fixed-default-on-read behavior the flyweight
 * generator cannot produce, since generated builders only default missing fields on write.
 */
public final class DescribeClusterResponseV0FW implements DescribeClusterResponse
{
    private static final int FIELD_SIZE_THROTTLE = 4;
    private static final int FIELD_SIZE_ERROR = 2;
    private static final int FIELD_SIZE_CONTROLLER_ID = 4;

    private final Varuint32FW taggedFieldsRO = new Varuint32FW();
    private final VarStringFW messageRO = new VarStringFW();
    private final VarStringFW clusterIdRO = new VarStringFW();
    private final Varuint32nFW brokerCountRO = new Varuint32nFW();
    private final ClusterBrokerFW brokerRO = new ClusterBrokerFW();
    private final DescribeClusterResponsePart2FW describeClusterResponsePart2RO = new DescribeClusterResponsePart2FW();

    private final BrokerView brokerView = new BrokerView();

    private DirectBufferEx buffer;
    private int progress;
    private int limit;

    private int throttleTimeMillis;
    private short error;
    private int messageOffset;
    private int messageLength;
    private int clusterIdOffset;
    private int clusterIdLength;
    private int controllerId;
    private int brokerCount;
    private int brokersRemaining;
    private boolean responseClosed;
    private int authorizedOperations;

    private int brokerId;
    private int brokerHostOffset;
    private int brokerHostLength;
    private int brokerPort;
    private int brokerRackOffset;
    private int brokerRackLength;

    /**
     * Wraps a complete DescribeCluster v0 response body: tagged fields, throttle time, error,
     * message, cluster id, controller id, and broker count, followed by the brokers themselves.
     */
    public DescribeClusterResponseV0FW wrap(
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        int progress = offset;

        final Varuint32FW taggedFields = taggedFieldsRO.wrap(buffer, progress, limit);
        progress = taggedFields.limit();

        final int throttleTimeMillis = buffer.getInt(progress, ByteOrder.BIG_ENDIAN);
        progress += FIELD_SIZE_THROTTLE;

        final short error = buffer.getShort(progress, ByteOrder.BIG_ENDIAN);
        progress += FIELD_SIZE_ERROR;

        final VarStringFW message = messageRO.wrap(buffer, progress, limit);
        progress = message.limit();

        final VarStringFW clusterId = clusterIdRO.wrap(buffer, progress, limit);
        progress = clusterId.limit();

        final int controllerId = buffer.getInt(progress, ByteOrder.BIG_ENDIAN);
        progress += FIELD_SIZE_CONTROLLER_ID;

        final Varuint32nFW brokerCount = brokerCountRO.wrap(buffer, progress, limit);
        progress = brokerCount.limit();

        this.buffer = buffer;
        this.limit = limit;
        this.progress = progress;
        this.throttleTimeMillis = throttleTimeMillis;
        this.error = error;
        this.messageOffset = message.offset() + message.fieldSizeLength();
        this.messageLength = message.length();
        this.clusterIdOffset = clusterId.offset() + clusterId.fieldSizeLength();
        this.clusterIdLength = clusterId.length();
        this.controllerId = controllerId;
        this.brokerCount = brokerCount.value();
        this.brokersRemaining = brokerCount.value();
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
    public int throttleTimeMillis()
    {
        return throttleTimeMillis;
    }

    @Override
    public short error()
    {
        return error;
    }

    @Override
    public int messageOffset()
    {
        return messageOffset;
    }

    @Override
    public int messageLength()
    {
        return messageLength;
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
    public int brokerCount()
    {
        return brokerCount;
    }

    @Override
    public boolean hasNext()
    {
        if (brokersRemaining == 0 && !responseClosed)
        {
            final DescribeClusterResponsePart2FW responsePart2 = describeClusterResponsePart2RO.wrap(buffer, progress, limit);
            progress = responsePart2.limit();
            authorizedOperations = responsePart2.clusterAuthorizedOperations();
            responseClosed = true;
        }

        return brokersRemaining != 0;
    }

    @Override
    public Broker next()
    {
        brokersRemaining--;

        final ClusterBrokerFW broker = brokerRO.wrap(buffer, progress, limit);
        progress = broker.limit();

        final VarStringFW host = broker.host();
        final VarStringFW rack = broker.rack();

        this.brokerId = broker.brokerId();
        this.brokerHostOffset = host.offset() + host.fieldSizeLength();
        this.brokerHostLength = host.length();
        this.brokerPort = broker.port();
        this.brokerRackOffset = rack.offset() + rack.fieldSizeLength();
        this.brokerRackLength = rack.length();

        return brokerView;
    }

    @Override
    public int authorizedOperations()
    {
        return authorizedOperations;
    }

    private final class BrokerView implements Broker
    {
        @Override
        public DirectBufferEx buffer()
        {
            return buffer;
        }

        @Override
        public int brokerId()
        {
            return brokerId;
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
}
