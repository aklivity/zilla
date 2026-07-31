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
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.describe_configs_v4.ConfigResponseFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.describe_configs_v4.ConfigResponsePart2FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.describe_configs_v4.DescribeConfigsResponsePart2FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.describe_configs_v4.ResourceResponseFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.describe_configs_v4.ResourceResponsePart2FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.describe_configs_v4.SynonymResponseFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;

/**
 * Hand-crafted (not {@code .idl}-generated) DescribeConfigs v4 response cursor. Delegates the actual
 * byte decoding to the generated {@code protocol.idl} wire types, adding the version-tolerant
 * {@link KafkaDescribeConfigsResponse} view on top, mirroring {@link CreateTopicsResponseV7FW}. Each
 * config's synonyms (and v3+ config type / documentation) are decoded and discarded inline as part
 * of landing on that config, since {@link KafkaDescribeConfigsResponse.Config} does not surface them.
 */
public final class KafkaDescribeConfigsResponseV4FW implements KafkaDescribeConfigsResponse
{
    private static final int FIELD_SIZE_THROTTLE_TIME_MILLIS = 4;

    private final Varuint32FW taggedFieldsRO = new Varuint32FW();
    private final Varuint32nFW resourceCountRO = new Varuint32nFW();
    private final ResourceResponseFW resourceResponseRO = new ResourceResponseFW();
    private final ConfigResponseFW configResponseRO = new ConfigResponseFW();
    private final SynonymResponseFW synonymResponseRO = new SynonymResponseFW();
    private final ConfigResponsePart2FW configResponsePart2RO = new ConfigResponsePart2FW();
    private final ResourceResponsePart2FW resourceResponsePart2RO = new ResourceResponsePart2FW();
    private final DescribeConfigsResponsePart2FW describeConfigsResponsePart2RO = new DescribeConfigsResponsePart2FW();

    private final ResourceView resourceView = new ResourceView();
    private final ConfigView configView = new ConfigView();

    private DirectBufferEx buffer;
    private int progress;
    private int limit;

    private int throttleTimeMillis;
    private int resourceCount;
    private int resourcesRemaining;
    private int configsRemaining;
    private boolean resourceOpen;
    private boolean responseClosed;

    private short resourceError;
    private int resourceMessageOffset;
    private int resourceMessageLength;
    private byte resourceType;
    private int resourceNameOffset;
    private int resourceNameLength;
    private int resourceConfigCount;

    private int configNameOffset;
    private int configNameLength;
    private int configValueOffset;
    private int configValueLength;
    private boolean configReadOnly;
    private byte configConfigSource;
    private boolean configIsSensitive;

    /**
     * Wraps a complete DescribeConfigs v4 response body: tagged fields, throttle time, and resource
     * count, followed by the resources themselves.
     */
    public KafkaDescribeConfigsResponseV4FW wrap(
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
        this.configsRemaining = 0;
        this.resourceOpen = false;
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
        // hasNext() consumes the per-resource and overall trailing tagged fields as the cursor
        // moves past the last config or resource that reaches them, so next() never needs to look
        // ahead.
        if (configsRemaining == 0 && resourceOpen)
        {
            final ResourceResponsePart2FW resourcePart2 = resourceResponsePart2RO.wrap(buffer, progress, limit);
            progress = resourcePart2.limit();
            resourceOpen = false;
        }

        if (resourcesRemaining == 0 && configsRemaining == 0 && !responseClosed)
        {
            final DescribeConfigsResponsePart2FW responsePart2 = describeConfigsResponsePart2RO.wrap(buffer, progress, limit);
            progress = responsePart2.limit();
            responseClosed = true;
        }

        return resourcesRemaining != 0 || configsRemaining != 0;
    }

    @Override
    public Kind next()
    {
        final Kind kind;

        if (configsRemaining != 0)
        {
            configsRemaining--;

            final ConfigResponseFW config = configResponseRO.wrap(buffer, progress, limit);
            progress = config.limit();

            final VarStringFW name = config.name();
            final VarStringFW value = config.value();

            this.configNameOffset = name.offset() + name.fieldSizeLength();
            this.configNameLength = name.length();
            this.configValueOffset = value.offset() + value.fieldSizeLength();
            this.configValueLength = value.length();
            this.configReadOnly = config.readOnly() != 0;
            this.configConfigSource = config.configSource();
            this.configIsSensitive = config.isSensitive() != 0;

            int synonymsRemaining = config.synonymCount();
            while (synonymsRemaining > 0)
            {
                final SynonymResponseFW synonym = synonymResponseRO.wrap(buffer, progress, limit);
                progress = synonym.limit();
                synonymsRemaining--;
            }

            final ConfigResponsePart2FW configPart2 = configResponsePart2RO.wrap(buffer, progress, limit);
            progress = configPart2.limit();

            kind = Kind.CONFIG;
        }
        else
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
            this.resourceConfigCount = resource.configCount();

            this.configsRemaining = resourceConfigCount;
            this.resourceOpen = true;

            kind = Kind.RESOURCE;
        }

        return kind;
    }

    @Override
    public Resource resource()
    {
        return resourceView;
    }

    @Override
    public Config config()
    {
        return configView;
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

        @Override
        public int configCount()
        {
            return resourceConfigCount;
        }
    }

    private final class ConfigView implements Config
    {
        @Override
        public DirectBufferEx buffer()
        {
            return buffer;
        }

        @Override
        public int nameOffset()
        {
            return configNameOffset;
        }

        @Override
        public int nameLength()
        {
            return configNameLength;
        }

        @Override
        public int valueOffset()
        {
            return configValueOffset;
        }

        @Override
        public int valueLength()
        {
            return configValueLength;
        }

        @Override
        public boolean readOnly()
        {
            return configReadOnly;
        }

        @Override
        public byte configSource()
        {
            return configConfigSource;
        }

        @Override
        public boolean isSensitive()
        {
            return configIsSensitive;
        }
    }
}
