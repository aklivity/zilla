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

import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx.Completion;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;
import io.aklivity.zilla.runtime.common.json.view.JsonStringView;

final class LlmEventCopier
{
    static final int NO_TAKEN = -1;

    private int depth;

    void reset()
    {
        depth = 0;
    }

    int depth()
    {
        return depth;
    }

    int copy(
        JsonGeneratorEx target,
        JsonEvent event,
        JsonParserEx parser)
    {
        int taken = NO_TAKEN;

        switch (event)
        {
        case START_OBJECT:
            target.writeStartObject();
            depth++;
            break;
        case START_ARRAY:
            target.writeStartArray();
            depth++;
            break;
        case END_OBJECT, END_ARRAY:
            target.writeEnd();
            depth--;
            break;
        case VALUE_TRUE:
            target.write(true);
            break;
        case VALUE_FALSE:
            target.write(false);
            break;
        case VALUE_NULL:
            target.writeNull();
            break;
        default:
            final boolean deferred = parser.deferredBytes();
            final JsonStringView chars = parser.getStringView();
            final Completion completion = deferred ? Completion.INCOMPLETE : Completion.COMPLETE;
            final int before = target.consumed();

            if (event == JsonEvent.KEY_NAME)
            {
                target.writeKey(chars, completion);
            }
            else if (event == JsonEvent.VALUE_NUMBER)
            {
                target.writeNumber(chars, completion);
            }
            else
            {
                target.write(chars, completion);
            }

            final int consumed = target.consumed() - before;

            if (consumed < chars.length() || deferred)
            {
                taken = consumed;
            }
            break;
        }

        return taken;
    }
}
