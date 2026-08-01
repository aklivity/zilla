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
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.describe_acls_v2.AclResponseFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.describe_acls_v2.DescribeAclsResponsePart2FW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.describe_acls_v2.ResourceResponseFW;
import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.describe_acls_v2.ResourceResponsePart2FW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;

/**
 * Hand-crafted (not {@code .idl}-generated) DescribeAcls v2 response cursor. Delegates the actual
 * byte decoding to the generated {@code protocol.idl} wire types, adding the version-tolerant
 * {@link KafkaDescribeAclsResponse} view on top, mirroring {@link KafkaDescribeConfigsResponseV4FW}.
 */
public final class KafkaDescribeAclsResponseV2FW implements KafkaDescribeAclsResponse
{
    private static final int FIELD_SIZE_THROTTLE_TIME_MILLIS = 4;
    private static final int FIELD_SIZE_ERROR = 2;

    private final Varuint32FW taggedFieldsRO = new Varuint32FW();
    private final VarStringFW messageRO = new VarStringFW();
    private final Varuint32nFW resourceCountRO = new Varuint32nFW();
    private final ResourceResponseFW resourceResponseRO = new ResourceResponseFW();
    private final AclResponseFW aclResponseRO = new AclResponseFW();
    private final ResourceResponsePart2FW resourceResponsePart2RO = new ResourceResponsePart2FW();
    private final DescribeAclsResponsePart2FW describeAclsResponsePart2RO = new DescribeAclsResponsePart2FW();

    private final ResourceView resourceView = new ResourceView();
    private final AclView aclView = new AclView();

    private DirectBufferEx buffer;
    private int progress;
    private int limit;

    private int throttleTimeMillis;
    private short error;
    private int messageOffset;
    private int messageLength;
    private int resourceCount;
    private int resourcesRemaining;
    private int aclsRemaining;
    private boolean resourceOpen;
    private boolean responseClosed;

    private byte resourceType;
    private int resourceNameOffset;
    private int resourceNameLength;
    private byte resourcePatternType;
    private int resourceAclCount;

    private int aclPrincipalOffset;
    private int aclPrincipalLength;
    private int aclHostOffset;
    private int aclHostLength;
    private byte aclOperation;
    private byte aclPermissionType;

    /**
     * Wraps a complete DescribeAcls v2 response body: tagged fields, throttle time, request-level
     * error and message, and resource count, followed by the resources themselves.
     */
    public KafkaDescribeAclsResponseV2FW wrap(
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        int progress = offset;

        final Varuint32FW taggedFields = taggedFieldsRO.wrap(buffer, progress, limit);
        progress = taggedFields.limit();

        final int throttleTimeMillis = buffer.getInt(progress, ByteOrder.BIG_ENDIAN);
        progress += FIELD_SIZE_THROTTLE_TIME_MILLIS;

        final short error = buffer.getShort(progress, ByteOrder.BIG_ENDIAN);
        progress += FIELD_SIZE_ERROR;

        final VarStringFW message = messageRO.wrap(buffer, progress, limit);
        progress = message.limit();

        final Varuint32nFW resourceCount = resourceCountRO.wrap(buffer, progress, limit);
        progress = resourceCount.limit();

        this.buffer = buffer;
        this.limit = limit;
        this.progress = progress;
        this.throttleTimeMillis = throttleTimeMillis;
        this.error = error;
        this.messageOffset = message.offset() + message.fieldSizeLength();
        this.messageLength = message.length();
        this.resourceCount = resourceCount.value();
        this.resourcesRemaining = resourceCount.value();
        this.aclsRemaining = 0;
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
    public int resourceCount()
    {
        return resourceCount;
    }

    @Override
    public boolean hasNext()
    {
        // hasNext() consumes the per-resource and overall trailing tagged fields as the cursor
        // moves past the last ACL or resource that reaches them, so next() never needs to look ahead.
        if (aclsRemaining == 0 && resourceOpen)
        {
            final ResourceResponsePart2FW resourcePart2 = resourceResponsePart2RO.wrap(buffer, progress, limit);
            progress = resourcePart2.limit();
            resourceOpen = false;
        }

        if (resourcesRemaining == 0 && aclsRemaining == 0 && !responseClosed)
        {
            final DescribeAclsResponsePart2FW responsePart2 = describeAclsResponsePart2RO.wrap(buffer, progress, limit);
            progress = responsePart2.limit();
            responseClosed = true;
        }

        return resourcesRemaining != 0 || aclsRemaining != 0;
    }

    @Override
    public Kind next()
    {
        final Kind kind;

        if (aclsRemaining != 0)
        {
            aclsRemaining--;

            final AclResponseFW acl = aclResponseRO.wrap(buffer, progress, limit);
            progress = acl.limit();

            final VarStringFW principal = acl.principal();
            final VarStringFW host = acl.host();

            this.aclPrincipalOffset = principal.offset() + principal.fieldSizeLength();
            this.aclPrincipalLength = principal.length();
            this.aclHostOffset = host.offset() + host.fieldSizeLength();
            this.aclHostLength = host.length();
            this.aclOperation = (byte) acl.operation();
            this.aclPermissionType = (byte) acl.permissionType();

            kind = Kind.ACL;
        }
        else
        {
            resourcesRemaining--;

            final ResourceResponseFW resource = resourceResponseRO.wrap(buffer, progress, limit);
            progress = resource.limit();

            final VarStringFW name = resource.name();

            this.resourceType = (byte) resource.type();
            this.resourceNameOffset = name.offset() + name.fieldSizeLength();
            this.resourceNameLength = name.length();
            this.resourcePatternType = (byte) resource.patternType();
            this.resourceAclCount = resource.aclCount();

            this.aclsRemaining = resourceAclCount;
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
    public Acl acl()
    {
        return aclView;
    }

    private final class ResourceView implements Resource
    {
        @Override
        public DirectBufferEx buffer()
        {
            return buffer;
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
        public byte patternType()
        {
            return resourcePatternType;
        }

        @Override
        public int aclCount()
        {
            return resourceAclCount;
        }
    }

    private final class AclView implements Acl
    {
        @Override
        public DirectBufferEx buffer()
        {
            return buffer;
        }

        @Override
        public int principalOffset()
        {
            return aclPrincipalOffset;
        }

        @Override
        public int principalLength()
        {
            return aclPrincipalLength;
        }

        @Override
        public int hostOffset()
        {
            return aclHostOffset;
        }

        @Override
        public int hostLength()
        {
            return aclHostLength;
        }

        @Override
        public byte operation()
        {
            return aclOperation;
        }

        @Override
        public byte permissionType()
        {
            return aclPermissionType;
        }
    }
}
