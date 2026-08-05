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
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.alter_configs_v2.AlterConfigsResponsePart2FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.alter_configs_v2.ResourceResponseFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;

/**
 * Hand-crafted (not {@code .idl}-generated) AlterConfigs v2 response cursor. Delegates the actual
 * byte decoding to the generated {@code protocol.idl} wire types, adding the version-tolerant
 * {@link KafkaAlterConfigsResponse} view on top, mirroring {@link DeleteTopicsResponseV6FW}.
 */
public final class KafkaAlterConfigsResponseV2FW implements KafkaAlterConfigsResponse
{
    private static final int FIELD_SIZE_THROTTLE_TIME_MILLIS = 4;

    private final Varuint32FW taggedFieldsRO = new Varuint32FW();
    private final Varuint32nFW resourceCountRO = new Varuint32nFW();
    private final ResourceResponseFW resourceResponseRO = new ResourceResponseFW();
    private final AlterConfigsResponsePart2FW alterConfigsResponsePart2RO = new AlterConfigsResponsePart2FW();

    private final ResourceView resourceView = new ResourceView();

    private DirectBufferEx buffer;
    private int progress;
    private int limit;

    private int throttleTimeMillis;
    private int resourceCount;
    private int resourcesRemaining;
    private boolean responseClosed;

    private short resourceError;
    private int resourceMessageOffset;
    private int resourceMessageLength;
    private byte resourceType;
    private int resourceNameOffset;
    private int resourceNameLength;

    /**
     * Wraps a complete AlterConfigs v2 response body: tagged fields, throttle time, and resource
     * count, followed by the resources themselves.
     */
    public KafkaAlterConfigsResponseV2FW wrap(
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        int progress = offset;

        final Varuint32FW taggedFields = taggedFieldsRO.wrap(buffer, progress, limit);
        progress = taggedFields.limit();

        final int throttleTimeMillis = buffer.getInt(progress, ByteOrder.BIG_ENDIAN);
        progress += FIELD_SIZE_THROTTLE_TIME_MILLIS;

        final Varuint32nFW resourceCount = resourceCountRO.wrap(buffer, progress, limit);
        progress = resourceCount.limit();

        this.buffer = buffer;
        this.limit = limit;
        this.progress = progress;
        this.throttleTimeMillis = throttleTimeMillis;
        this.resourceCount = resourceCount.value();
        this.resourcesRemaining = resourceCount.value();
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
    public int resourceCount()
    {
        return resourceCount;
    }

    @Override
    public boolean hasNext()
    {
        if (resourcesRemaining == 0 && !responseClosed)
        {
            final AlterConfigsResponsePart2FW responsePart2 = alterConfigsResponsePart2RO.wrap(buffer, progress, limit);
            progress = responsePart2.limit();
            responseClosed = true;
        }

        return resourcesRemaining != 0;
    }

    @Override
    public Resource next()
    {
        resourcesRemaining--;

        final ResourceResponseFW resource = resourceResponseRO.wrap(buffer, progress, limit);
        progress = resource.limit();

        final VarStringFW message = resource.message();
        final VarStringFW name = resource.name();

        this.resourceError = resource.error();
        this.resourceMessageOffset = message.offset() + message.fieldSizeLength();
        this.resourceMessageLength = message.length();
        this.resourceType = (byte) resource.type();
        this.resourceNameOffset = name.offset() + name.fieldSizeLength();
        this.resourceNameLength = name.length();

        return resourceView;
    }

    private final class ResourceView implements Resource
    {
        @Override
        public DirectBufferEx buffer()
        {
            return buffer;
        }

        @Override
        public short error()
        {
            return resourceError;
        }

        @Override
        public int messageOffset()
        {
            return resourceMessageOffset;
        }

        @Override
        public int messageLength()
        {
            return resourceMessageLength;
        }

        @Override
        public byte type()
        {
            return resourceType;
        }

        @Override
        public int nameOffset()
        {
            return resourceNameOffset;
        }

        @Override
        public int nameLength()
        {
            return resourceNameLength;
        }
    }
}
