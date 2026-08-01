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
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.delete_acls_v2.DeleteAclsResponsePart2FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.delete_acls_v2.FilterResultResponseFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.delete_acls_v2.FilterResultResponsePart2FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.delete_acls_v2.MatchingAclResponseFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;

/**
 * Hand-crafted (not {@code .idl}-generated) DeleteAcls v2 response cursor. Delegates the actual byte
 * decoding to the generated {@code protocol.idl} wire types, adding the version-tolerant
 * {@link KafkaDeleteAclsResponse} view on top, mirroring {@link KafkaDescribeAclsResponseV2FW}.
 */
public final class KafkaDeleteAclsResponseV2FW implements KafkaDeleteAclsResponse
{
    private static final int FIELD_SIZE_THROTTLE_TIME_MILLIS = 4;

    private final Varuint32FW taggedFieldsRO = new Varuint32FW();
    private final Varuint32nFW filterResultCountRO = new Varuint32nFW();
    private final FilterResultResponseFW filterResultResponseRO = new FilterResultResponseFW();
    private final MatchingAclResponseFW matchingAclResponseRO = new MatchingAclResponseFW();
    private final FilterResultResponsePart2FW filterResultResponsePart2RO = new FilterResultResponsePart2FW();
    private final DeleteAclsResponsePart2FW deleteAclsResponsePart2RO = new DeleteAclsResponsePart2FW();

    private final FilterResultView filterResultView = new FilterResultView();
    private final MatchingAclView matchingAclView = new MatchingAclView();

    private DirectBufferEx buffer;
    private int progress;
    private int limit;

    private int throttleTimeMillis;
    private int filterResultCount;
    private int filterResultsRemaining;
    private int matchingAclsRemaining;
    private boolean filterResultOpen;
    private boolean responseClosed;

    private short filterResultError;
    private int filterResultMessageOffset;
    private int filterResultMessageLength;
    private int filterResultMatchingAclCount;

    private short matchingAclError;
    private int matchingAclMessageOffset;
    private int matchingAclMessageLength;
    private byte matchingAclResourceType;
    private int matchingAclResourceNameOffset;
    private int matchingAclResourceNameLength;
    private byte matchingAclPatternType;
    private int matchingAclPrincipalOffset;
    private int matchingAclPrincipalLength;
    private int matchingAclHostOffset;
    private int matchingAclHostLength;
    private byte matchingAclOperation;
    private byte matchingAclPermissionType;

    /**
     * Wraps a complete DeleteAcls v2 response body: tagged fields, throttle time, and filter result
     * count, followed by the filter results themselves.
     */
    public KafkaDeleteAclsResponseV2FW wrap(
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        int progress = offset;

        final Varuint32FW taggedFields = taggedFieldsRO.wrap(buffer, progress, limit);
        progress = taggedFields.limit();

        final int throttleTimeMillis = buffer.getInt(progress, ByteOrder.BIG_ENDIAN);
        progress += FIELD_SIZE_THROTTLE_TIME_MILLIS;

        final Varuint32nFW filterResultCount = filterResultCountRO.wrap(buffer, progress, limit);
        progress = filterResultCount.limit();

        this.buffer = buffer;
        this.limit = limit;
        this.progress = progress;
        this.throttleTimeMillis = throttleTimeMillis;
        this.filterResultCount = filterResultCount.value();
        this.filterResultsRemaining = filterResultCount.value();
        this.matchingAclsRemaining = 0;
        this.filterResultOpen = false;
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
    public int filterResultCount()
    {
        return filterResultCount;
    }

    @Override
    public boolean hasNext()
    {
        // hasNext() consumes the per-filter-result and overall trailing tagged fields as the cursor
        // moves past the last matching ACL or filter result that reaches them, so next() never needs
        // to look ahead.
        if (matchingAclsRemaining == 0 && filterResultOpen)
        {
            final FilterResultResponsePart2FW filterResultPart2 = filterResultResponsePart2RO.wrap(buffer, progress, limit);
            progress = filterResultPart2.limit();
            filterResultOpen = false;
        }

        if (filterResultsRemaining == 0 && matchingAclsRemaining == 0 && !responseClosed)
        {
            final DeleteAclsResponsePart2FW responsePart2 = deleteAclsResponsePart2RO.wrap(buffer, progress, limit);
            progress = responsePart2.limit();
            responseClosed = true;
        }

        return filterResultsRemaining != 0 || matchingAclsRemaining != 0;
    }

    @Override
    public Kind next()
    {
        final Kind kind;

        if (matchingAclsRemaining != 0)
        {
            matchingAclsRemaining--;

            final MatchingAclResponseFW matchingAcl = matchingAclResponseRO.wrap(buffer, progress, limit);
            progress = matchingAcl.limit();

            final VarStringFW message = matchingAcl.message();
            final VarStringFW resourceName = matchingAcl.resourceName();
            final VarStringFW principal = matchingAcl.principal();
            final VarStringFW host = matchingAcl.host();

            this.matchingAclError = matchingAcl.error();
            this.matchingAclMessageOffset = message.offset() + message.fieldSizeLength();
            this.matchingAclMessageLength = message.length();
            this.matchingAclResourceType = (byte) matchingAcl.resourceType();
            this.matchingAclResourceNameOffset = resourceName.offset() + resourceName.fieldSizeLength();
            this.matchingAclResourceNameLength = resourceName.length();
            this.matchingAclPatternType = (byte) matchingAcl.patternType();
            this.matchingAclPrincipalOffset = principal.offset() + principal.fieldSizeLength();
            this.matchingAclPrincipalLength = principal.length();
            this.matchingAclHostOffset = host.offset() + host.fieldSizeLength();
            this.matchingAclHostLength = host.length();
            this.matchingAclOperation = (byte) matchingAcl.operation();
            this.matchingAclPermissionType = (byte) matchingAcl.permissionType();

            kind = Kind.MATCHING_ACL;
        }
        else
        {
            filterResultsRemaining--;

            final FilterResultResponseFW filterResult = filterResultResponseRO.wrap(buffer, progress, limit);
            progress = filterResult.limit();

            final VarStringFW message = filterResult.message();

            this.filterResultError = filterResult.error();
            this.filterResultMessageOffset = message.offset() + message.fieldSizeLength();
            this.filterResultMessageLength = message.length();
            this.filterResultMatchingAclCount = filterResult.matchingAclCount();

            this.matchingAclsRemaining = filterResultMatchingAclCount;
            this.filterResultOpen = true;

            kind = Kind.FILTER_RESULT;
        }

        return kind;
    }

    @Override
    public FilterResult filterResult()
    {
        return filterResultView;
    }

    @Override
    public MatchingAcl matchingAcl()
    {
        return matchingAclView;
    }

    private final class FilterResultView implements FilterResult
    {
        @Override
        public DirectBufferEx buffer()
        {
            return buffer;
        }

        @Override
        public short error()
        {
            return filterResultError;
        }

        @Override
        public int messageOffset()
        {
            return filterResultMessageOffset;
        }

        @Override
        public int messageLength()
        {
            return filterResultMessageLength;
        }

        @Override
        public int matchingAclCount()
        {
            return filterResultMatchingAclCount;
        }
    }

    private final class MatchingAclView implements MatchingAcl
    {
        @Override
        public DirectBufferEx buffer()
        {
            return buffer;
        }

        @Override
        public short error()
        {
            return matchingAclError;
        }

        @Override
        public int messageOffset()
        {
            return matchingAclMessageOffset;
        }

        @Override
        public int messageLength()
        {
            return matchingAclMessageLength;
        }

        @Override
        public byte resourceType()
        {
            return matchingAclResourceType;
        }

        @Override
        public int resourceNameOffset()
        {
            return matchingAclResourceNameOffset;
        }

        @Override
        public int resourceNameLength()
        {
            return matchingAclResourceNameLength;
        }

        @Override
        public byte patternType()
        {
            return matchingAclPatternType;
        }

        @Override
        public int principalOffset()
        {
            return matchingAclPrincipalOffset;
        }

        @Override
        public int principalLength()
        {
            return matchingAclPrincipalLength;
        }

        @Override
        public int hostOffset()
        {
            return matchingAclHostOffset;
        }

        @Override
        public int hostLength()
        {
            return matchingAclHostLength;
        }

        @Override
        public byte operation()
        {
            return matchingAclOperation;
        }

        @Override
        public byte permissionType()
        {
            return matchingAclPermissionType;
        }
    }
}
