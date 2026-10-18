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

import io.aklivity.zilla.runtime.binding.llm.internal.types.stream.LlmSectionType;
import io.aklivity.zilla.runtime.common.agrona.buffer.UnsafeBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;

final class LlmAttributeProbeRunner
{
    static final int WHOLE = 0;

    private static final int PART_PREFIX_EVENTS = 3;
    private static final int OBJECT_PREFIX_EVENTS = 2;

    static LlmAttributeProbe.Status run(
        LlmAttributeProbe probe,
        LlmSectionType type,
        String json,
        int window)
    {
        return run(probe, type, json, window, false, PART_PREFIX_EVENTS);
    }

    static LlmAttributeProbe.Status runObject(
        LlmAttributeProbe probe,
        LlmSectionType type,
        String json,
        int window,
        boolean toEnd)
    {
        return run(probe, type, json, window, toEnd, OBJECT_PREFIX_EVENTS);
    }

    static LlmAttributeProbe.Status runToEnd(
        LlmAttributeProbe probe,
        LlmSectionType type,
        String json,
        int window)
    {
        return run(probe, type, json, window, true, PART_PREFIX_EVENTS);
    }

    static LlmAttributeProbe.Status runResumed(
        LlmAttributeProbe probe,
        LlmSectionType type,
        String json,
        int window)
    {
        final byte[] bytes = json.getBytes(UTF_8);
        final UnsafeBufferEx buffer = new UnsafeBufferEx(bytes);
        final JsonParserEx parser = JsonEx.createParser();

        parser.wrap(buffer, 0, window, false);

        for (int event = 0; event < PART_PREFIX_EVENTS; event++)
        {
            parser.nextEvent();
        }

        probe.begin(type);

        LlmAttributeProbe.Status status = drain(probe, parser, false);

        if (status == LlmAttributeProbe.Status.PENDING)
        {
            parser.wrap(buffer, window - parser.remaining(), bytes.length, true);
            status = drain(probe, parser, false);
        }

        return status;
    }

    private static LlmAttributeProbe.Status run(
        LlmAttributeProbe probe,
        LlmSectionType type,
        String json,
        int window,
        boolean toEnd,
        int prefixEvents)
    {
        final byte[] bytes = json.getBytes(UTF_8);
        final UnsafeBufferEx buffer = new UnsafeBufferEx(bytes);
        final JsonParserEx parser = JsonEx.createParser();

        if (window == WHOLE)
        {
            parser.wrap(buffer, 0, bytes.length, true);
        }
        else
        {
            parser.wrap(buffer, 0, Math.min(window, bytes.length), false);
        }

        for (int event = 0; event < prefixEvents; event++)
        {
            parser.nextEvent();
        }

        probe.begin(type);

        return drain(probe, parser, toEnd);
    }

    private static LlmAttributeProbe.Status drain(
        LlmAttributeProbe probe,
        JsonParserEx parser,
        boolean toEnd)
    {
        LlmAttributeProbe.Status status = LlmAttributeProbe.Status.PENDING;
        while ((toEnd ? status != LlmAttributeProbe.Status.COMPLETE : status == LlmAttributeProbe.Status.PENDING) &&
            parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            status = probe.onEvent(event, parser);
        }

        return status;
    }

    private LlmAttributeProbeRunner()
    {
    }
}
