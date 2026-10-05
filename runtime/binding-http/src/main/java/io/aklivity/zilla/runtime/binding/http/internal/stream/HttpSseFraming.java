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
package io.aklivity.zilla.runtime.binding.http.internal.stream;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.function.Consumer;

import io.aklivity.zilla.runtime.binding.http.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.binding.http.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;

final class HttpSseFraming
{
    static final String HEADER_NAME = "zilla:framing";
    static final String HEADER_VALUE = "event-stream";

    private static final byte[] NAME = HEADER_NAME.getBytes(UTF_8);
    private static final byte[] VALUE = HEADER_VALUE.getBytes(UTF_8);

    private final HttpBeginExFW.Builder beginExRW = new HttpBeginExFW.Builder();
    private final Consumer<HttpHeaderFW> detectHeader = this::detectHeader;
    private final Consumer<HttpHeaderFW> retainHeader = this::retainHeader;

    private boolean detected;

    boolean detect(
        HttpBeginExFW beginEx)
    {
        detected = false;
        beginEx.headers().forEach(detectHeader);

        return detected;
    }

    HttpBeginExFW strip(
        HttpBeginExFW beginEx,
        MutableDirectBufferEx buffer,
        int httpTypeId)
    {
        beginExRW.wrap(buffer, 0, buffer.capacity()).typeId(httpTypeId);
        beginEx.headers().forEach(retainHeader);

        return beginExRW.build();
    }

    static boolean equalsBytes(
        DirectBufferEx value,
        byte[] expected)
    {
        final int length = value != null ? value.capacity() : 0;

        boolean equals = length == expected.length;

        for (int i = 0; equals && i < length; i++)
        {
            equals = value.getByte(i) == expected[i];
        }

        return equals;
    }

    private void detectHeader(
        HttpHeaderFW header)
    {
        detected |= equalsBytes(header.name().value(), NAME) && equalsBytes(header.value().value(), VALUE);
    }

    private void retainHeader(
        HttpHeaderFW header)
    {
        if (!equalsBytes(header.name().value(), NAME))
        {
            beginExRW.headersItem(i -> i.name(header.name()).value(header.value()));
        }
    }
}
