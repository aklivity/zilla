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

import java.util.Arrays;

import io.aklivity.zilla.runtime.common.json.JsonEvent;

final class LlmCapturedValue
{
    static final int ABSENT = -1;

    private static final int INITIAL_CAPACITY = 32;
    private static final int EVENT_COST = 4;

    private JsonEvent[] events;
    private String[] texts;
    private int count;
    private int size;

    LlmCapturedValue()
    {
        this.events = new JsonEvent[INITIAL_CAPACITY];
        this.texts = new String[INITIAL_CAPACITY];
    }

    void clear()
    {
        count = 0;
        size = 0;
    }

    boolean add(
        JsonEvent event,
        String text,
        int limit)
    {
        size += EVENT_COST + (text != null ? text.length() : 0);

        final boolean accepted = size <= limit;

        if (accepted)
        {
            if (count == events.length)
            {
                events = Arrays.copyOf(events, count << 1);
                texts = Arrays.copyOf(texts, count << 1);
            }

            events[count] = event;
            texts[count] = text;
            count++;
        }

        return accepted;
    }

    int count()
    {
        return count;
    }

    JsonEvent event(
        int index)
    {
        return index >= 0 && index < count ? events[index] : null;
    }

    String text(
        int index)
    {
        return index >= 0 && index < count ? texts[index] : null;
    }

    String string(
        int index)
    {
        return event(index) == JsonEvent.VALUE_STRING ? texts[index] : null;
    }

    boolean isString(
        int index,
        String expected)
    {
        return expected.equals(string(index));
    }

    boolean isObject(
        int index)
    {
        return event(index) == JsonEvent.START_OBJECT;
    }

    boolean isArray(
        int index)
    {
        return event(index) == JsonEvent.START_ARRAY;
    }

    int end(
        int index)
    {
        int depth = 0;
        int next = index;

        do
        {
            switch (events[next])
            {
            case START_OBJECT:
            case START_ARRAY:
                depth++;
                break;
            case END_OBJECT:
            case END_ARRAY:
                depth--;
                break;
            default:
                break;
            }

            next++;
        }
        while (depth > 0 && next < count);

        return next;
    }

    int member(
        int object,
        String key)
    {
        int found = ABSENT;

        if (isObject(object))
        {
            int index = object + 1;

            while (found == ABSENT && events[index] == JsonEvent.KEY_NAME)
            {
                if (key.equals(texts[index]))
                {
                    found = index + 1;
                }

                index = end(index + 1);
            }
        }

        return found;
    }

    boolean onlyMembers(
        int object,
        String[] keys)
    {
        boolean only = isObject(object);
        int index = object + 1;

        while (only && events[index] == JsonEvent.KEY_NAME)
        {
            only = contains(keys, texts[index]);
            index = end(index + 1);
        }

        return only;
    }

    private static boolean contains(
        String[] keys,
        String key)
    {
        boolean contained = false;

        for (int index = 0; !contained && index < keys.length; index++)
        {
            contained = keys[index].equals(key);
        }

        return contained;
    }
}
