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

import static java.nio.charset.StandardCharsets.UTF_8;

import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;

final class A2aDialectEncoder
{
    static final String ENVELOPE = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"";
    static final String PARAMS = ",\"params\":";

    private MutableDirectBufferEx output;
    private int limit;
    private int members;

    A2aDialectEncoder wrap(
        MutableDirectBufferEx output,
        int offset)
    {
        this.output = output;
        this.limit = offset;
        return this;
    }

    int limit()
    {
        return limit;
    }

    A2aDialectEncoder method(
        String method)
    {
        raw(ENVELOPE);
        raw(method);
        raw("\"");
        return this;
    }

    A2aDialectEncoder params()
    {
        return raw(PARAMS);
    }

    A2aDialectEncoder end()
    {
        return raw("}");
    }

    A2aDialectEncoder raw(
        String text)
    {
        limit += output.putStringWithoutLengthAscii(limit, text);
        return this;
    }

    A2aDialectEncoder object(
        String tenant)
    {
        raw("{");
        members = 0;
        return tenant != null ? member("tenant", tenant) : this;
    }

    A2aDialectEncoder member(
        String name,
        String value)
    {
        raw(members++ > 0 ? ",\"" : "\"");
        raw(name);
        raw("\":\"");
        string(value);
        raw("\"");
        return this;
    }

    A2aDialectEncoder member(
        String name,
        int value)
    {
        raw(members++ > 0 ? ",\"" : "\"");
        raw(name);
        raw("\":");
        limit += output.putIntAscii(limit, value);
        return this;
    }

    A2aDialectEncoder endObject()
    {
        return raw("}");
    }

    private void string(
        String value)
    {
        for (byte b : value.getBytes(UTF_8))
        {
            switch (b)
            {
            case '"':
                raw("\\\"");
                break;
            case '\\':
                raw("\\\\");
                break;
            default:
                output.putByte(limit++, b);
                break;
            }
        }
    }
}
