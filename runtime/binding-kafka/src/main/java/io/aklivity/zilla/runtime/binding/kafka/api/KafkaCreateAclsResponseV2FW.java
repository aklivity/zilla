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
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.create_acls_v2.AclCreationResultResponseFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.create_acls_v2.CreateAclsResponsePart2FW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;

/**
 * Hand-crafted (not {@code .idl}-generated) CreateAcls v2 response cursor. Delegates the actual byte
 * decoding to the generated {@code protocol.idl} wire types, adding the version-tolerant
 * {@link KafkaCreateAclsResponse} view on top, mirroring {@link KafkaAlterConfigsResponseV2FW}.
 */
public final class KafkaCreateAclsResponseV2FW implements KafkaCreateAclsResponse
{
    private static final int FIELD_SIZE_THROTTLE_TIME_MILLIS = 4;

    private final Varuint32FW taggedFieldsRO = new Varuint32FW();
    private final Varuint32nFW resultCountRO = new Varuint32nFW();
    private final AclCreationResultResponseFW aclCreationResultResponseRO = new AclCreationResultResponseFW();
    private final CreateAclsResponsePart2FW createAclsResponsePart2RO = new CreateAclsResponsePart2FW();

    private final ResultView resultView = new ResultView();

    private DirectBufferEx buffer;
    private int progress;
    private int limit;

    private int throttleTimeMillis;
    private int resultCount;
    private int resultsRemaining;
    private boolean responseClosed;

    private short resultError;
    private int resultMessageOffset;
    private int resultMessageLength;

    /**
     * Wraps a complete CreateAcls v2 response body: tagged fields, throttle time, and result count,
     * followed by the results themselves.
     */
    public KafkaCreateAclsResponseV2FW wrap(
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        int progress = offset;

        final Varuint32FW taggedFields = taggedFieldsRO.wrap(buffer, progress, limit);
        progress = taggedFields.limit();

        final int throttleTimeMillis = buffer.getInt(progress, ByteOrder.BIG_ENDIAN);
        progress += FIELD_SIZE_THROTTLE_TIME_MILLIS;

        final Varuint32nFW resultCount = resultCountRO.wrap(buffer, progress, limit);
        progress = resultCount.limit();

        this.buffer = buffer;
        this.limit = limit;
        this.progress = progress;
        this.throttleTimeMillis = throttleTimeMillis;
        this.resultCount = resultCount.value();
        this.resultsRemaining = resultCount.value();
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
    public int resultCount()
    {
        return resultCount;
    }

    @Override
    public boolean hasNext()
    {
        if (resultsRemaining == 0 && !responseClosed)
        {
            final CreateAclsResponsePart2FW responsePart2 = createAclsResponsePart2RO.wrap(buffer, progress, limit);
            progress = responsePart2.limit();
            responseClosed = true;
        }

        return resultsRemaining != 0;
    }

    @Override
    public Result next()
    {
        resultsRemaining--;

        final AclCreationResultResponseFW result = aclCreationResultResponseRO.wrap(buffer, progress, limit);
        progress = result.limit();

        final VarStringFW message = result.message();

        this.resultError = result.error();
        this.resultMessageOffset = message.offset() + message.fieldSizeLength();
        this.resultMessageLength = message.length();

        return resultView;
    }

    private final class ResultView implements Result
    {
        @Override
        public DirectBufferEx buffer()
        {
            return buffer;
        }

        @Override
        public short error()
        {
            return resultError;
        }

        @Override
        public int messageOffset()
        {
            return resultMessageOffset;
        }

        @Override
        public int messageLength()
        {
            return resultMessageLength;
        }
    }
}
