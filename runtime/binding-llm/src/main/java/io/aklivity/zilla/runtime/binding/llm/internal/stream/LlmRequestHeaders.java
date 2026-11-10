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
package io.aklivity.zilla.runtime.binding.llm.internal.stream;

import java.util.function.Consumer;

import io.aklivity.zilla.runtime.binding.llm.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.HttpBeginExFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEnvelope;

// The headers of a request, read where they lie in the begin extension. One instance is repointed at each request,
// and a value it returns is valid only until the next lookup or the next request.
final class LlmRequestHeaders implements JsonEnvelope
{
    private final UnsafeBufferEx valueRO;
    private final Consumer<HttpHeaderFW> tally;
    private final Consumer<HttpHeaderFW> locate;

    private HttpBeginExFW httpBeginEx;
    private String name;
    private int index;
    private int seen;
    private boolean found;

    LlmRequestHeaders()
    {
        this.valueRO = new UnsafeBufferEx();
        this.tally = this::onTally;
        this.locate = this::onLocate;
    }

    LlmRequestHeaders wrap(
        HttpBeginExFW httpBeginEx)
    {
        this.httpBeginEx = httpBeginEx;
        return this;
    }

    @Override
    public int count(
        String name)
    {
        this.name = name;
        this.seen = 0;

        httpBeginEx.headers().forEach(tally);

        return seen;
    }

    @Override
    public DirectBufferEx get(
        String name,
        int index)
    {
        this.name = name;
        this.index = index;
        this.seen = 0;
        this.found = false;

        httpBeginEx.headers().forEach(locate);

        return found ? valueRO : null;
    }

    @Override
    public void set(
        String name,
        DirectBufferEx value)
    {
        throw new UnsupportedOperationException("request headers are read-only");
    }

    private void onTally(
        HttpHeaderFW header)
    {
        if (named(header))
        {
            seen++;
        }
    }

    private void onLocate(
        HttpHeaderFW header)
    {
        if (!found && named(header))
        {
            final DirectBufferEx value = header.value().value();

            if (seen == index && value != null)
            {
                valueRO.wrap(value);
                found = true;
            }

            seen++;
        }
    }

    private boolean named(
        HttpHeaderFW header)
    {
        final DirectBufferEx key = header.name().value();

        boolean matches = key != null && key.capacity() == name.length();

        for (int position = 0; matches && position < name.length(); position++)
        {
            matches = key.getByte(position) == name.charAt(position);
        }

        return matches;
    }
}
