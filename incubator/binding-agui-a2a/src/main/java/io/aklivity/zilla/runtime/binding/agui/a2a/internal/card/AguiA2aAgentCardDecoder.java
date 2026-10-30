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
package io.aklivity.zilla.runtime.binding.agui.a2a.internal.card;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.json.JsonException;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;

public final class AguiA2aAgentCardDecoder
{
    private final JsonParserEx parser = JsonEx.createParser(Map.of());
    private final List<String> inputModes = new ArrayList<>();

    private final AguiA2aAgentCardDecode decodeStart = this::decodeStart;
    private final AguiA2aAgentCardDecode decodeRootStart = this::decodeRootStart;
    private final AguiA2aAgentCardDecode decodeRoot = this::decodeRoot;
    private final AguiA2aAgentCardDecode decodeCapabilitiesStart = this::decodeCapabilitiesStart;
    private final AguiA2aAgentCardDecode decodeCapabilities = this::decodeCapabilities;
    private final AguiA2aAgentCardDecode decodeStreaming = this::decodeStreaming;
    private final AguiA2aAgentCardDecode decodeInputModesStart = this::decodeInputModesStart;
    private final AguiA2aAgentCardDecode decodeInputModes = this::decodeInputModes;
    private final AguiA2aAgentCardDecode decodeSkillsStart = this::decodeSkillsStart;
    private final AguiA2aAgentCardDecode decodeSkills = this::decodeSkills;
    private final AguiA2aAgentCardDecode decodeSkill = this::decodeSkill;
    private final AguiA2aAgentCardDecode decodeSkillInputModesStart = this::decodeSkillInputModesStart;
    private final AguiA2aAgentCardDecode decodeSkillInputModes = this::decodeSkillInputModes;
    private final AguiA2aAgentCardDecode decodeSkip = this::decodeSkip;
    private final AguiA2aAgentCardDecode decodeEnd = this::decodeEnd;
    private final AguiA2aAgentCardDecode decodeIgnore = this::decodeIgnore;

    private AguiA2aAgentCardDecode decoder = decodeStart;
    private AguiA2aAgentCardDecode skipThen;
    private int skipDepth;
    private boolean streaming;
    private boolean failed;

    public int decode(
        DirectBufferEx buffer,
        int offset,
        int limit,
        boolean last)
    {
        int progress = offset;

        if (decoder == decodeEnd || decoder == decodeIgnore)
        {
            progress = decoder.decode(buffer, offset, progress, limit);
        }
        else
        {
            parser.wrap(buffer, offset, limit, last);

            AguiA2aAgentCardDecode previousDecoder;
            int previous;

            try
            {
                do
                {
                    previous = progress;
                    previousDecoder = decoder;
                    progress = decoder.decode(buffer, offset, progress, limit);
                }
                while (progress != previous && progress < limit || decoder != previousDecoder);
            }
            catch (JsonException ex)
            {
                onDecodeParseError();
            }

            if (last && decoder != decodeEnd && decoder != decodeIgnore)
            {
                onDecodeParseError();
            }
        }

        return progress;
    }

    public boolean complete()
    {
        return decoder == decodeEnd;
    }

    public boolean failed()
    {
        return failed;
    }

    public AguiA2aAgentCard card()
    {
        return new AguiA2aAgentCard(streaming, List.copyOf(inputModes));
    }

    public void reset()
    {
        parser.reset();
        inputModes.clear();
        decoder = decodeStart;
        skipThen = null;
        skipDepth = 0;
        streaming = false;
        failed = false;
    }

    private int decodeStart(
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            if (event != JsonEvent.START_DOCUMENT)
            {
                onDecodeParseError();
                break decode;
            }

            decoder = decodeRootStart;
        }

        return position(limit);
    }

    private int decodeRootStart(
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            if (event != JsonEvent.START_OBJECT)
            {
                onDecodeParseError();
                break decode;
            }

            decoder = decodeRoot;
        }

        return position(limit);
    }

    private int decodeRoot(
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            switch (event)
            {
            case KEY_NAME:
                if (!parser.deferredBytes())
                {
                    onDecodeRootKey(parser.getStringView());
                }
                break;
            case END_OBJECT:
                decoder = decodeEnd;
                break;
            default:
                onDecodeParseError();
                break decode;
            }
        }

        return position(limit);
    }

    private int decodeCapabilitiesStart(
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            if (event == JsonEvent.START_OBJECT)
            {
                decoder = decodeCapabilities;
            }
            else
            {
                skipUnexpected(event, decodeRoot);
            }
        }

        return position(limit);
    }

    private int decodeCapabilities(
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            switch (event)
            {
            case KEY_NAME:
                if (!parser.deferredBytes())
                {
                    if ("streaming".contentEquals(parser.getStringView()))
                    {
                        decoder = decodeStreaming;
                    }
                    else
                    {
                        skip(decodeCapabilities);
                    }
                }
                break;
            case END_OBJECT:
                decoder = decodeRoot;
                break;
            default:
                onDecodeParseError();
                break decode;
            }
        }

        return position(limit);
    }

    private int decodeStreaming(
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            if (event == JsonEvent.VALUE_TRUE || event == JsonEvent.VALUE_FALSE)
            {
                streaming = event == JsonEvent.VALUE_TRUE;
                decoder = decodeCapabilities;
            }
            else
            {
                skipUnexpected(event, decodeCapabilities);
            }
        }

        return position(limit);
    }

    private int decodeInputModesStart(
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            if (event == JsonEvent.START_ARRAY)
            {
                decoder = decodeInputModes;
            }
            else
            {
                skipUnexpected(event, decodeRoot);
            }
        }

        return position(limit);
    }

    private int decodeInputModes(
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        decodeModes(decodeRoot);

        return position(limit);
    }

    private int decodeSkillsStart(
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            if (event == JsonEvent.START_ARRAY)
            {
                decoder = decodeSkills;
            }
            else
            {
                skipUnexpected(event, decodeRoot);
            }
        }

        return position(limit);
    }

    private int decodeSkills(
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            switch (event)
            {
            case START_OBJECT:
                decoder = decodeSkill;
                break;
            case END_ARRAY:
                decoder = decodeRoot;
                break;
            default:
                skipUnexpected(event, decodeSkills);
                break;
            }
        }

        return position(limit);
    }

    private int decodeSkill(
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        decode:
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            switch (event)
            {
            case KEY_NAME:
                if (!parser.deferredBytes())
                {
                    final CharSequence key = parser.getStringView();
                    if ("inputModes".contentEquals(key) || "input_modes".contentEquals(key))
                    {
                        decoder = decodeSkillInputModesStart;
                    }
                    else
                    {
                        skip(decodeSkill);
                    }
                }
                break;
            case END_OBJECT:
                decoder = decodeSkills;
                break;
            default:
                onDecodeParseError();
                break decode;
            }
        }

        return position(limit);
    }

    private int decodeSkillInputModesStart(
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            if (event == JsonEvent.START_ARRAY)
            {
                decoder = decodeSkillInputModes;
            }
            else
            {
                skipUnexpected(event, decodeSkill);
            }
        }

        return position(limit);
    }

    private int decodeSkillInputModes(
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        decodeModes(decodeSkill);

        return position(limit);
    }

    private int decodeSkip(
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            switch (event)
            {
            case START_OBJECT:
            case START_ARRAY:
                skipDepth++;
                break;
            case END_OBJECT:
            case END_ARRAY:
                skipDepth--;
                break;
            default:
                break;
            }

            final boolean fragment = event == JsonEvent.VALUE_STRING && parser.deferredBytes();

            if (fragment)
            {
                parser.consumed(parser.getStringView().length());
            }
            else if (event != JsonEvent.KEY_NAME && skipDepth == 0)
            {
                decoder = skipThen;
            }
        }

        return position(limit);
    }

    private int decodeEnd(
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        int index = progress;

        while (index < limit && isWhitespace(buffer.getByte(index)))
        {
            index++;
        }

        if (index < limit)
        {
            onDecodeParseError();
        }

        return limit;
    }

    private int decodeIgnore(
        DirectBufferEx buffer,
        int offset,
        int progress,
        int limit)
    {
        return limit;
    }

    private void decodeModes(
        AguiA2aAgentCardDecode then)
    {
        if (parser.hasNextEvent())
        {
            final JsonEvent event = parser.nextEvent();
            switch (event)
            {
            case VALUE_STRING:
                if (!parser.deferredBytes())
                {
                    final String mode = AguiA2aAgentCard.normalize(parser.getString());
                    if (!mode.isEmpty())
                    {
                        inputModes.add(mode);
                    }
                }
                break;
            case END_ARRAY:
                decoder = then;
                break;
            default:
                skipUnexpected(event, decoder);
                break;
            }
        }
    }

    private void onDecodeRootKey(
        CharSequence key)
    {
        if ("capabilities".contentEquals(key))
        {
            decoder = decodeCapabilitiesStart;
        }
        else if ("defaultInputModes".contentEquals(key))
        {
            decoder = decodeInputModesStart;
        }
        else if ("skills".contentEquals(key))
        {
            decoder = decodeSkillsStart;
        }
        else
        {
            skip(decodeRoot);
        }
    }

    private void skip(
        AguiA2aAgentCardDecode then)
    {
        skipThen = then;
        skipDepth = 0;
        decoder = decodeSkip;
    }

    private void skipUnexpected(
        JsonEvent event,
        AguiA2aAgentCardDecode then)
    {
        final boolean container = event == JsonEvent.START_OBJECT || event == JsonEvent.START_ARRAY;

        if (container)
        {
            skipThen = then;
            skipDepth = 1;
            decoder = decodeSkip;
        }
        else
        {
            decoder = then;
        }
    }

    private void onDecodeParseError()
    {
        failed = true;
        decoder = decodeIgnore;
    }

    private int position(
        int limit)
    {
        return limit - parser.remaining();
    }

    private static boolean isWhitespace(
        byte value)
    {
        return value == ' ' || value == '\t' || value == '\r' || value == '\n';
    }

    @FunctionalInterface
    private interface AguiA2aAgentCardDecode
    {
        int decode(
            DirectBufferEx buffer,
            int offset,
            int progress,
            int limit);
    }
}
