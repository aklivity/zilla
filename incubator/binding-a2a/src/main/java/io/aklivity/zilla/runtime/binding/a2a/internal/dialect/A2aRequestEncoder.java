/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.a2a.internal.dialect;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;

public final class A2aRequestEncoder
{
    static final String ENVELOPE = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"";
    static final String PARAMS = ",\"params\":";

    private MutableDirectBufferEx output;
    private int limit;

    public A2aRequestEncoder wrap(
        MutableDirectBufferEx output,
        int offset)
    {
        this.output = output;
        this.limit = offset;
        return this;
    }

    public int limit()
    {
        return limit;
    }

    public A2aRequestEncoder method(
        String method)
    {
        raw(ENVELOPE);
        raw(method);
        raw("\"");
        return this;
    }

    public A2aRequestEncoder params()
    {
        return raw(PARAMS);
    }

    public A2aRequestEncoder end()
    {
        return raw("}");
    }

    public A2aRequestEncoder raw(
        String text)
    {
        limit += output.putStringWithoutLengthUtf8(limit, text);
        return this;
    }
}
