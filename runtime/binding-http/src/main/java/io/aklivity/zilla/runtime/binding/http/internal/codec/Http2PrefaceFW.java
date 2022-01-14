/*
 * Copyright 2021-2022 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.http.internal.codec;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.binding.http.internal.types.Flyweight;

/*
 *  Flyweight for HTTP2 client preface
 */
public class Http2PrefaceFW extends Flyweight
{

    public static final byte[] PRI_REQUEST =
    {
        'P', 'R', 'I', ' ', '*', ' ', 'H', 'T', 'T', 'P', '/', '2', '.', '0', '\r', '\n',
        '\r', '\n',
        'S', 'M', '\r', '\n',
        '\r', '\n'
    };
    private static final DirectBuffer PREFACE = new UnsafeBuffer(PRI_REQUEST);

    private final DirectBuffer payloadRO = new UnsafeBuffer(new byte[0]);

    @Override
    public int limit()
    {
        return offset() + PRI_REQUEST.length;
    }

    public boolean error()
    {
        return !PREFACE.equals(payloadRO);
    }

    public Http2PrefaceFW tryWrap(
        DirectBuffer buffer,
        int offset,
        int maxLimit)
    {
        // TODO: super.tryWrap != null
        boolean wrappable = super.wrap(buffer, offset, maxLimit) != null;

        wrappable &= PRI_REQUEST.length <= maxLimit - offset;
        if (wrappable)
        {
            payloadRO.wrap(buffer, offset, PRI_REQUEST.length);

            checkLimit(limit(), maxLimit);
        }

        return wrappable ? this : null;
    }

    @Override
    public Http2PrefaceFW wrap(
        DirectBuffer buffer,
        int offset,
        int maxLimit)
    {
        super.wrap(buffer, offset, maxLimit);

        payloadRO.wrap(buffer, offset, PRI_REQUEST.length);

        checkLimit(limit(), maxLimit);

        return this;
    }

}

