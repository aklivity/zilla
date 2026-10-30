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

import jakarta.json.stream.JsonParser.Event;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;

public final class AguiA2aAgentCardParser
{
    private final JsonParserEx parser = JsonEx.createParser(Map.of());

    public AguiA2aAgentCard parse(
        DirectBufferEx buffer,
        int offset,
        int limit)
    {
        parser.wrap(buffer, offset, limit);

        final List<String> inputModes = new ArrayList<>();

        String topKey = null;
        String itemKey = null;
        String capabilityKey = null;
        boolean streaming = false;
        int depth = 0;

        while (parser.hasNext())
        {
            final Event event = parser.next();
            switch (event)
            {
            case START_OBJECT:
            case START_ARRAY:
                depth++;
                if (depth == 3)
                {
                    itemKey = null;
                }
                break;
            case END_OBJECT:
            case END_ARRAY:
                depth--;
                break;
            case KEY_NAME:
                if (depth == 1)
                {
                    topKey = parser.getString();
                }
                else if (depth == 2 && "capabilities".equals(topKey))
                {
                    capabilityKey = parser.getString();
                }
                else if (depth == 3 && "skills".equals(topKey))
                {
                    itemKey = parser.getString();
                }
                break;
            case VALUE_TRUE:
            case VALUE_FALSE:
                if (depth == 2 && "capabilities".equals(topKey) && "streaming".equals(capabilityKey))
                {
                    streaming = event == Event.VALUE_TRUE;
                }
                break;
            case VALUE_STRING:
                if (depth == 2 && "defaultInputModes".equals(topKey) ||
                    depth == 4 && "skills".equals(topKey) && ("inputModes".equals(itemKey) || "input_modes".equals(itemKey)))
                {
                    final String mode = AguiA2aAgentCard.normalize(parser.getString());
                    if (mode != null && !mode.isEmpty())
                    {
                        inputModes.add(mode);
                    }
                }
                break;
            default:
                break;
            }
        }
        parser.reset();

        return new AguiA2aAgentCard(streaming, inputModes);
    }
}
