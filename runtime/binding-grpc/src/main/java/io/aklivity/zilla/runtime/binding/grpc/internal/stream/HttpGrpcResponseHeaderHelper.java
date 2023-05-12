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

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.agrona.AsciiSequenceView;
import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.binding.grpc.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.String8FW;
import io.aklivity.zilla.runtime.binding.grpc.internal.types.stream.HttpBeginExFW;

public final class HttpGrpcResponseHeaderHelper
{
    private static final String8FW HTTP_HEADER_STATUS = new String8FW(":status");
    private static final String8FW HTTP_HEADER_GRPC_STATUS = new String8FW("grpc-status");
    private static final String8FW HEADER_NAME_CONTENT_TYPE = new String8FW("content-type");

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

    public CharSequence contentType;
    public String16FW status;
    public String16FW grpcStatus;

    public void visit(
        HttpBeginExFW beginEx)
    {
        status = null;
        grpcStatus = null;
        contentType = null;

        if (beginEx != null)
        {
            beginEx.headers().forEach(this::dispatch);
        }
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
}
