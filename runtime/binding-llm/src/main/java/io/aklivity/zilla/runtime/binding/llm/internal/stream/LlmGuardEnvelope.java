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

import static java.nio.charset.StandardCharsets.UTF_8;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.engine.model.ModelEnvelope;

// Attributes supplied to each guardrail stage model:
//   direction  "request" or "response", the direction of the exchange the stage is applied to
//   exchange   the exchange id as a UTF-8 decimal string, the initial id of the exchange stream, so the same for
//              the request and the response of one exchange and distinct across exchanges
final class LlmGuardEnvelope implements ModelEnvelope
{
    static final String DIRECTION = "direction";
    static final String EXCHANGE = "exchange";
    static final String DIRECTION_REQUEST = "request";
    static final String DIRECTION_RESPONSE = "response";

    private final DirectBufferEx direction;
    private final DirectBufferEx exchange;

    LlmGuardEnvelope(
        String direction,
        long exchangeId)
    {
        this.direction = new UnsafeBufferEx(direction.getBytes(UTF_8));
        this.exchange = new UnsafeBufferEx(Long.toString(exchangeId).getBytes(UTF_8));
    }

    @Override
    public int count(
        String name)
    {
        return DIRECTION.equals(name) || EXCHANGE.equals(name) ? 1 : 0;
    }

    @Override
    public DirectBufferEx get(
        String name,
        int index)
    {
        DirectBufferEx value = null;

        if (index == 0)
        {
            if (DIRECTION.equals(name))
            {
                value = direction;
            }
            else if (EXCHANGE.equals(name))
            {
                value = exchange;
            }
        }

        return value;
    }

    @Override
    public void set(
        String name,
        DirectBufferEx value)
    {
    }
}
