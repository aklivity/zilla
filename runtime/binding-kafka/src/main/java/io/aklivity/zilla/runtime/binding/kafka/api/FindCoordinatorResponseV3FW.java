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

import io.aklivity.zilla.runtime.binding.kafka.internal.types.VarStringFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.find_coordinator.FindCoordinatorResponseFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;

/**
 * Hand-crafted (not {@code .idl}-generated) FindCoordinator v3 response, adding the
 * version-tolerant {@link FindCoordinatorResponse} view over the single generated flyweight -
 * unlike the array-shaped responses elsewhere in this package, FindCoordinator has exactly one
 * result per response, so there is no cursor to drive.
 */
public final class FindCoordinatorResponseV3FW implements FindCoordinatorResponse
{
    private final FindCoordinatorResponseFW findCoordinatorResponseRO = new FindCoordinatorResponseFW();

    private DirectBufferEx buffer;
    private int limit;

    private int throttleTimeMillis;
    private short error;
    private int messageOffset;
    private int messageLength;
    private int nodeId;
    private int hostOffset;
    private int hostLength;
    private int port;

    public FindCoordinatorResponseV3FW wrap(
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        final FindCoordinatorResponseFW response = findCoordinatorResponseRO.wrap(buffer, offset, limit);

        final VarStringFW message = response.message();
        final VarStringFW host = response.host();

        this.buffer = buffer;
        this.limit = response.limit();
        this.throttleTimeMillis = response.throttleTimeMillis();
        this.error = response.error();
        this.messageOffset = message.offset() + message.fieldSizeLength();
        this.messageLength = message.length();
        this.nodeId = response.nodeId();
        this.hostOffset = host.offset() + host.fieldSizeLength();
        this.hostLength = host.length();
        this.port = response.port();

        return this;
    }

    /**
     * @return the offset just past the last byte consumed
     */
    public int limit()
    {
        return limit;
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
    public int nodeId()
    {
        return nodeId;
    }

    @Override
    public int hostOffset()
    {
        return hostOffset;
    }

    @Override
    public int hostLength()
    {
        return hostLength;
    }

    @Override
    public int port()
    {
        return port;
    }
}
