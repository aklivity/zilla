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
package io.aklivity.zilla.runtime.metrics.http.internal;

import static io.aklivity.zilla.runtime.metrics.http.internal.HttpUtils.initialId;

import org.agrona.collections.Long2LongHashMap;

import io.aklivity.zilla.runtime.metrics.http.internal.types.stream.AbortFW;
import io.aklivity.zilla.runtime.metrics.http.internal.types.stream.BeginFW;
import io.aklivity.zilla.runtime.metrics.http.internal.types.stream.EndFW;
import io.aklivity.zilla.runtime.metrics.http.internal.types.stream.ResetFW;

final class HttpExchanges
{
    enum Event
    {
        NONE,
        REQUEST,
        RESPONSE,
        COMPLETED,
        REJECTED,
        DISCARDED
    }

    private static final long MISSING = -1L;
    private static final long REQUEST_ENDED = 0b01L;
    private static final long RESPONDING = 0b10L;

    private final Long2LongHashMap starts;
    private final Long2LongHashMap states;
    private long duration;

    HttpExchanges()
    {
        this.starts = new Long2LongHashMap(MISSING);
        this.states = new Long2LongHashMap(MISSING);
    }

    Event onFrame(
        int msgTypeId,
        long streamId,
        long timestamp)
    {
        final long exchangeId = initialId(streamId);
        final boolean request = HttpUtils.direction(streamId) == 1L;
        final long state = states.get(exchangeId);
        Event event = Event.NONE;
        if (msgTypeId == BeginFW.TYPE_ID && request)
        {
            if (timestamp != 0L)
            {
                starts.put(exchangeId, timestamp);
                states.put(exchangeId, 0L);
                event = Event.REQUEST;
            }
        }
        else if (state != MISSING)
        {
            switch (msgTypeId)
            {
            case BeginFW.TYPE_ID:
                states.put(exchangeId, state | RESPONDING);
                event = Event.RESPONSE;
                break;
            case EndFW.TYPE_ID:
                if (request)
                {
                    states.put(exchangeId, state | REQUEST_ENDED);
                }
                else
                {
                    event = close(exchangeId, timestamp, Event.COMPLETED);
                }
                break;
            case AbortFW.TYPE_ID:
                event = close(exchangeId, timestamp, Event.DISCARDED);
                break;
            case ResetFW.TYPE_ID:
                if (!request)
                {
                    event = close(exchangeId, timestamp, Event.DISCARDED);
                }
                else if ((state & RESPONDING) == 0L)
                {
                    event = close(exchangeId, timestamp, (state & REQUEST_ENDED) != 0L ? Event.REJECTED : Event.DISCARDED);
                }
                break;
            default:
                break;
            }
        }
        return event;
    }

    long duration()
    {
        return duration;
    }

    private Event close(
        long exchangeId,
        long timestamp,
        Event event)
    {
        states.remove(exchangeId);
        duration = timestamp - starts.remove(exchangeId);
        return event;
    }
}
