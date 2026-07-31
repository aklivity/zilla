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
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.list_groups.GroupResponseFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.list_groups.ListGroupsResponsePart2FW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;

/**
 * Hand-crafted (not {@code .idl}-generated) ListGroups v4 response cursor. Delegates the actual
 * byte decoding to the generated {@code protocol.idl} wire types, adding the version-tolerant
 * {@link ListGroupsResponse} view on top - a fixed-default-on-read behavior the flyweight
 * generator cannot produce, since generated builders only default missing fields on write.
 */
public final class ListGroupsResponseV4FW implements ListGroupsResponse
{
    private static final int FIELD_SIZE_THROTTLE_TIME_MILLIS = 4;
    private static final int FIELD_SIZE_ERROR = 2;

    private final Varuint32FW taggedFieldsRO = new Varuint32FW();
    private final Varuint32nFW groupCountRO = new Varuint32nFW();
    private final GroupResponseFW groupResponseRO = new GroupResponseFW();
    private final ListGroupsResponsePart2FW listGroupsResponsePart2RO = new ListGroupsResponsePart2FW();

    private final GroupView groupView = new GroupView();

    private DirectBufferEx buffer;
    private int progress;
    private int limit;

    private short error;
    private int groupCount;
    private int groupsRemaining;
    private boolean responseClosed;

    private int groupIdOffset;
    private int groupIdLength;
    private int protocolTypeOffset;
    private int protocolTypeLength;
    private int groupStateOffset;
    private int groupStateLength;

    /**
     * Wraps a complete ListGroups v4 response body: tagged fields, throttle time, error code, and
     * group count, followed by the groups themselves.
     */
    public ListGroupsResponseV4FW wrap(
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        int progress = offset;

        final Varuint32FW taggedFields = taggedFieldsRO.wrap(buffer, progress, limit);
        progress = taggedFields.limit();

        progress += FIELD_SIZE_THROTTLE_TIME_MILLIS;

        final short error = buffer.getShort(progress, ByteOrder.BIG_ENDIAN);
        progress += FIELD_SIZE_ERROR;

        final Varuint32nFW groupCount = groupCountRO.wrap(buffer, progress, limit);
        progress = groupCount.limit();

        return wrapGroups(buffer, progress, limit, error, groupCount.value());
    }

    /**
     * Wraps just the groups of a ListGroups v4 response body, for a caller that has already decoded
     * the throttle time, error code, and group count itself (e.g. via a generated header flyweight
     * covering a wider response envelope, such as one that also carries a correlation id).
     */
    public ListGroupsResponseV4FW wrapGroups(
        DirectBufferEx buffer,
        int offset,
        int limit,
        short error,
        int groupCount)
    {
        this.buffer = buffer;
        this.limit = limit;
        this.progress = offset;
        this.error = error;
        this.groupCount = groupCount;
        this.groupsRemaining = groupCount;
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
    public short error()
    {
        return error;
    }

    @Override
    public int groupCount()
    {
        return groupCount;
    }

    @Override
    public boolean hasNext()
    {
        if (groupsRemaining == 0 && !responseClosed)
        {
            final ListGroupsResponsePart2FW responsePart2 = listGroupsResponsePart2RO.wrap(buffer, progress, limit);
            progress = responsePart2.limit();
            responseClosed = true;
        }

        return groupsRemaining != 0;
    }

    @Override
    public Group next()
    {
        groupsRemaining--;

        final GroupResponseFW group = groupResponseRO.wrap(buffer, progress, limit);
        progress = group.limit();

        final VarStringFW groupId = group.groupId();
        final VarStringFW protocolType = group.protocolType();
        final VarStringFW groupState = group.groupState();

        this.groupIdOffset = groupId.offset() + groupId.fieldSizeLength();
        this.groupIdLength = groupId.length();
        this.protocolTypeOffset = protocolType.offset() + protocolType.fieldSizeLength();
        this.protocolTypeLength = protocolType.length();
        this.groupStateOffset = groupState.offset() + groupState.fieldSizeLength();
        this.groupStateLength = groupState.length();

        return groupView;
    }

    private final class GroupView implements Group
    {
        @Override
        public DirectBufferEx buffer()
        {
            return buffer;
        }

        @Override
        public int groupIdOffset()
        {
            return groupIdOffset;
        }

        @Override
        public int groupIdLength()
        {
            return groupIdLength;
        }

        @Override
        public int protocolTypeOffset()
        {
            return protocolTypeOffset;
        }

        @Override
        public int protocolTypeLength()
        {
            return protocolTypeLength;
        }

        @Override
        public int groupStateOffset()
        {
            return groupStateOffset;
        }

        @Override
        public int groupStateLength()
        {
            return groupStateLength;
        }
    }
}
