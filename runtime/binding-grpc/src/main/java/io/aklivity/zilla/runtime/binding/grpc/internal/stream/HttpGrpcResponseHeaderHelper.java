/*
 * Copyright 2021-2023 Aklivity Inc
 *
 * Licensed under the Aklivity Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   https://www.aklivity.io/aklivity-community-license/
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.aklivity.zilla.runtime.binding.grpc.internal.stream;

import static io.aklivity.zilla.runtime.binding.grpc.internal.types.stream.GrpcType.BASE64;
import static io.aklivity.zilla.runtime.binding.grpc.internal.types.stream.GrpcType.TEXT;
import static java.util.Arrays.asList;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.agrona.AsciiSequenceView;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import io.aklivity.zilla.runtime.binding.grpc.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.String8FW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.stream.GrpcMetadataFW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.stream.GrpcType;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.stream.HttpBeginExFW;

public final class HttpGrpcResponseHeaderHelper
{
    private static final String8FW HTTP_HEADER_STATUS = new String8FW(":status");
    private static final String8FW HTTP_HEADER_GRPC_STATUS = new String8FW("grpc-status");
    private static final String8FW HEADER_NAME_CONTENT_TYPE = new String8FW("content-type");

    private static final byte[] GRPC_PREFIX = "grpc-".getBytes();
    private static final byte[] BIN_SUFFIX = "-bin".getBytes();
    private static final int GRPC_PREFIX_LENGTH = GRPC_PREFIX.length;
    private static final int BIN_SUFFIX_LENGTH = BIN_SUFFIX.length;

    private final Array32FW.Builder<GrpcMetadataFW.Builder, GrpcMetadataFW> grpcMetadataRW =
        new Array32FW.Builder<>(new GrpcMetadataFW.Builder(), new GrpcMetadataFW());

    private final Set<String8FW> httpHeaders =
        new HashSet<>(asList(new String8FW(":path"),
            new String8FW(":method"),
            new String8FW(":status"),
            new String8FW(":scheme"),
            new String8FW(":authority"),
            new String8FW("service-name"),
            new String8FW("te"),
            new String8FW("content-type"),
            new String8FW("user-agent")));

    private final Map<String8FW, Consumer<String16FW>> visitors;
    {
        Map<String8FW, Consumer<String16FW>> visitors = new HashMap<>();
        visitors.put(HTTP_HEADER_STATUS, this::visitStatus);
        visitors.put(HTTP_HEADER_GRPC_STATUS, this::visitGrpcStatus);
        visitors.put(HEADER_NAME_CONTENT_TYPE, this::visitContentType);
        this.visitors = visitors;
    }
    private final AsciiSequenceView contentTypeRO = new AsciiSequenceView();
    private final String16FW statusRO = new String16FW();
    private final String16FW grpcStatusRO = new String16FW();
    private final byte[] headerPrefix = new byte[GRPC_PREFIX_LENGTH];
    private final byte[] headerSuffix = new byte[BIN_SUFFIX_LENGTH];
    private final MutableDirectBuffer metadataBuffer;

    public CharSequence contentType;
    public String16FW status;
    public String16FW grpcStatus;
    public Array32FW<GrpcMetadataFW> metadata;

    public HttpGrpcResponseHeaderHelper(
        MutableDirectBuffer metadataBuffer)
    {
        this.metadataBuffer = metadataBuffer;
    }

    public void visit(
        HttpBeginExFW beginEx)
    {
        status = null;
        grpcStatus = null;
        contentType = null;
        metadata = null;
        grpcMetadataRW.wrap(metadataBuffer, 0, metadataBuffer.capacity());

        if (beginEx != null)
        {
            beginEx.headers().forEach(this::dispatch);
        }

        metadata = grpcMetadataRW.build();
    }

    private boolean dispatch(
        HttpHeaderFW header)
    {
        final String8FW name = header.name();
        final Consumer<String16FW> visitor = visitors.get(name);
        if (visitor != null)
        {
            visitor.accept(header.value());
        }

        visitHeader(header);

        return status != null &&
            grpcStatus != null &&
            contentType != null;
    }

    private void visitContentType(
        String16FW value)
    {
        final DirectBuffer buffer = value.buffer();
        final int offset = value.offset() + value.fieldSizeLength();
        final int length = value.sizeof() - value.fieldSizeLength();
        contentType = contentTypeRO.wrap(buffer, offset, length);
    }

    private void visitStatus(
        String16FW value)
    {
        status = statusRO.wrap(value.buffer(), value.offset(), value.limit());
    }

    private void visitGrpcStatus(
        String16FW value)
    {
        grpcStatus = grpcStatusRO.wrap(value.buffer(), value.offset(), value.limit());
    }

    private void visitHeader(
        HttpHeaderFW header)
    {
        final String8FW name = header.name();
        final String16FW value = header.value();
        final boolean notHttpHeader = !httpHeaders.contains(name);

        final int offset = name.offset();
        final int limit = name.limit();
        name.buffer().getBytes(offset, headerPrefix);
        name.buffer().getBytes(limit - BIN_SUFFIX.length, headerSuffix);

        if (notHttpHeader && !GRPC_PREFIX.equals(headerPrefix))
        {
            final GrpcType type = Arrays.equals(BIN_SUFFIX, headerSuffix) ? BASE64 : TEXT;
            final int metadataNameLength = type == BASE64 ? name.length() - BIN_SUFFIX.length : name.length();

            grpcMetadataRW.item(m -> m.type(t -> t.set(type))
                .nameLen(metadataNameLength)
                .name(name.value(), 0, metadataNameLength)
                .valueLen(value.length())
                .value(value.value(), 0, value.length()));
        }
    }
}
