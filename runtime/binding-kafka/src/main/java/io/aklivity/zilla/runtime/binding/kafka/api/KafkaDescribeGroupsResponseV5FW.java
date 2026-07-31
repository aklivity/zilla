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
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.describe_groups.DescribeGroupsResponsePart2FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.describe_groups.DescribedGroupMemberResponseFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.describe_groups.DescribedGroupResponseFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.describe_groups.DescribedGroupResponsePart2FW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;

/**
 * Hand-crafted (not {@code .idl}-generated) DescribeGroups v5 response cursor. Delegates the actual
 * byte decoding to the generated {@code protocol.idl} wire types, adding the version-tolerant
 * {@link KafkaDescribeGroupsResponse} view on top - a fixed-default-on-read behavior the flyweight
 * generator cannot produce, since generated builders only default missing fields on write.
 * <p>
 * {@code memberMetadata} and {@code memberAssignment} are Kafka COMPACT_BYTES fields; since no
 * compact-bytes primitive exists in the flyweight grammar, this class decodes their varint
 * length-prefix ({@code Varuint32nFW}, the same "N+1, 0=null" encoding COMPACT_STRING already uses)
 * and slices the raw payload directly, rather than modeling them in {@code protocol.idl}.
 * </p>
 */
public final class KafkaDescribeGroupsResponseV5FW implements KafkaDescribeGroupsResponse
{
    private static final int FIELD_SIZE_THROTTLE_TIME_MILLIS = 4;

    private final Varuint32FW taggedFieldsRO = new Varuint32FW();
    private final Varuint32nFW groupCountRO = new Varuint32nFW();
    private final Varuint32nFW memberCountRO = new Varuint32nFW();
    private final Varuint32nFW payloadLengthRO = new Varuint32nFW();
    private final DescribedGroupResponseFW describedGroupResponseRO = new DescribedGroupResponseFW();
    private final DescribedGroupMemberResponseFW describedGroupMemberResponseRO = new DescribedGroupMemberResponseFW();
    private final DescribedGroupResponsePart2FW describedGroupResponsePart2RO = new DescribedGroupResponsePart2FW();
    private final DescribeGroupsResponsePart2FW describeGroupsResponsePart2RO = new DescribeGroupsResponsePart2FW();

    private final GroupView groupView = new GroupView();
    private final MemberView memberView = new MemberView();

    private DirectBufferEx buffer;
    private int progress;
    private int limit;

    private int throttleTimeMillis;
    private int groupCount;
    private int groupsRemaining;
    private int membersRemaining;
    private boolean groupOpen;
    private boolean responseClosed;

    private short groupError;
    private int groupIdOffset;
    private int groupIdLength;
    private int groupStateOffset;
    private int groupStateLength;
    private int protocolTypeOffset;
    private int protocolTypeLength;
    private int protocolDataOffset;
    private int protocolDataLength;
    private int groupMemberCount;

    private int memberIdOffset;
    private int memberIdLength;
    private int groupInstanceIdOffset;
    private int groupInstanceIdLength;
    private int clientIdOffset;
    private int clientIdLength;
    private int clientHostOffset;
    private int clientHostLength;
    private int memberMetadataOffset;
    private int memberMetadataLength;
    private int memberAssignmentOffset;
    private int memberAssignmentLength;

    /**
     * Wraps a complete DescribeGroups v5 response body: tagged fields, throttle time, and group
     * count, followed by the groups themselves.
     */
    public KafkaDescribeGroupsResponseV5FW wrap(
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        int progress = offset;

        final Varuint32FW taggedFields = taggedFieldsRO.wrap(buffer, progress, limit);
        progress = taggedFields.limit();

        final int throttleTimeMillis = buffer.getInt(progress, ByteOrder.BIG_ENDIAN);
        progress += FIELD_SIZE_THROTTLE_TIME_MILLIS;

        final Varuint32nFW groupCount = groupCountRO.wrap(buffer, progress, limit);
        progress = groupCount.limit();

        return wrapGroups(buffer, progress, limit, throttleTimeMillis, groupCount.value());
    }

    /**
     * Wraps just the groups of a DescribeGroups v5 response body, for a caller that has already
     * decoded the throttle time and group count itself (e.g. via a generated header flyweight
     * covering a wider response envelope, such as one that also carries a correlation id).
     */
    public KafkaDescribeGroupsResponseV5FW wrapGroups(
        DirectBufferEx buffer,
        int offset,
        int limit,
        int throttleTimeMillis,
        int groupCount)
    {
        this.buffer = buffer;
        this.limit = limit;
        this.progress = offset;
        this.throttleTimeMillis = throttleTimeMillis;
        this.groupCount = groupCount;
        this.groupsRemaining = groupCount;
        this.membersRemaining = 0;
        this.groupOpen = false;
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
    public int groupCount()
    {
        return groupCount;
    }

    @Override
    public boolean hasNext()
    {
        // hasNext() consumes the per-group and overall trailing tagged fields as the cursor moves
        // past the last member or group that reaches them, so next() never needs to look ahead.
        if (membersRemaining == 0 && groupOpen)
        {
            final DescribedGroupResponsePart2FW groupPart2 = describedGroupResponsePart2RO.wrap(buffer, progress, limit);
            progress = groupPart2.limit();
            groupOpen = false;
        }

        if (groupsRemaining == 0 && membersRemaining == 0 && !responseClosed)
        {
            final DescribeGroupsResponsePart2FW responsePart2 = describeGroupsResponsePart2RO.wrap(buffer, progress, limit);
            progress = responsePart2.limit();
            responseClosed = true;
        }

        return groupsRemaining != 0 || membersRemaining != 0;
    }

    @Override
    public Kind next()
    {
        final Kind kind;

        if (membersRemaining != 0)
        {
            membersRemaining--;

            final DescribedGroupMemberResponseFW member = describedGroupMemberResponseRO.wrap(buffer, progress, limit);
            progress = member.limit();

            final VarStringFW memberId = member.memberId();
            final VarStringFW groupInstanceId = member.groupInstanceId();
            final VarStringFW clientId = member.clientId();
            final VarStringFW clientHost = member.clientHost();

            this.memberIdOffset = memberId.offset() + memberId.fieldSizeLength();
            this.memberIdLength = memberId.length();
            this.groupInstanceIdOffset = groupInstanceId.offset() + groupInstanceId.fieldSizeLength();
            this.groupInstanceIdLength = groupInstanceId.length();
            this.clientIdOffset = clientId.offset() + clientId.fieldSizeLength();
            this.clientIdLength = clientId.length();
            this.clientHostOffset = clientHost.offset() + clientHost.fieldSizeLength();
            this.clientHostLength = clientHost.length();

            final Varuint32nFW metadataLength = payloadLengthRO.wrap(buffer, progress, limit);
            progress = metadataLength.limit();
            this.memberMetadataLength = metadataLength.value();
            this.memberMetadataOffset = progress;
            if (memberMetadataLength >= 0)
            {
                progress += memberMetadataLength;
            }

            final Varuint32nFW assignmentLength = payloadLengthRO.wrap(buffer, progress, limit);
            progress = assignmentLength.limit();
            this.memberAssignmentLength = assignmentLength.value();
            this.memberAssignmentOffset = progress;
            if (memberAssignmentLength >= 0)
            {
                progress += memberAssignmentLength;
            }

            final Varuint32FW memberTaggedFields = taggedFieldsRO.wrap(buffer, progress, limit);
            progress = memberTaggedFields.limit();

            kind = Kind.MEMBER;
        }
        else
        {
            groupsRemaining--;

            final DescribedGroupResponseFW group = describedGroupResponseRO.wrap(buffer, progress, limit);
            progress = group.limit();

            final VarStringFW groupId = group.groupId();
            final VarStringFW groupState = group.groupState();
            final VarStringFW protocolType = group.protocolType();
            final VarStringFW protocolData = group.protocolData();

            this.groupError = group.error();
            this.groupIdOffset = groupId.offset() + groupId.fieldSizeLength();
            this.groupIdLength = groupId.length();
            this.groupStateOffset = groupState.offset() + groupState.fieldSizeLength();
            this.groupStateLength = groupState.length();
            this.protocolTypeOffset = protocolType.offset() + protocolType.fieldSizeLength();
            this.protocolTypeLength = protocolType.length();
            this.protocolDataOffset = protocolData.offset() + protocolData.fieldSizeLength();
            this.protocolDataLength = protocolData.length();
            this.groupMemberCount = group.memberCount();

            this.membersRemaining = groupMemberCount;
            this.groupOpen = true;

            kind = Kind.GROUP;
        }

        return kind;
    }

    @Override
    public Group group()
    {
        return groupView;
    }

    @Override
    public Member member()
    {
        return memberView;
    }

    private final class GroupView implements Group
    {
        @Override
        public DirectBufferEx buffer()
        {
            return buffer;
        }

        @Override
        public short error()
        {
            return groupError;
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
        public int groupStateOffset()
        {
            return groupStateOffset;
        }

        @Override
        public int groupStateLength()
        {
            return groupStateLength;
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
        public int protocolDataOffset()
        {
            return protocolDataOffset;
        }

        @Override
        public int protocolDataLength()
        {
            return protocolDataLength;
        }

        @Override
        public int memberCount()
        {
            return groupMemberCount;
        }
    }

    private final class MemberView implements Member
    {
        @Override
        public DirectBufferEx buffer()
        {
            return buffer;
        }

        @Override
        public int memberIdOffset()
        {
            return memberIdOffset;
        }

        @Override
        public int memberIdLength()
        {
            return memberIdLength;
        }

        @Override
        public int groupInstanceIdOffset()
        {
            return groupInstanceIdOffset;
        }

        @Override
        public int groupInstanceIdLength()
        {
            return groupInstanceIdLength;
        }

        @Override
        public int clientIdOffset()
        {
            return clientIdOffset;
        }

        @Override
        public int clientIdLength()
        {
            return clientIdLength;
        }

        @Override
        public int clientHostOffset()
        {
            return clientHostOffset;
        }

        @Override
        public int clientHostLength()
        {
            return clientHostLength;
        }

        @Override
        public int memberMetadataOffset()
        {
            return memberMetadataOffset;
        }

        @Override
        public int memberMetadataLength()
        {
            return memberMetadataLength;
        }

        @Override
        public int memberAssignmentOffset()
        {
            return memberAssignmentOffset;
        }

        @Override
        public int memberAssignmentLength()
        {
            return memberAssignmentLength;
        }
    }
}
