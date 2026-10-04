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
import io.aklivity.zilla.runtime.common.json.JsonParserEx;

abstract class LlmAttributeProbe
{
    enum Status
    {
        PENDING,
        RESOLVED,
        COMPLETE,
        REJECTED
    }

    protected static final int NO_KEY = 0;

    private static final int MAX_DEPTH = 6;
    private static final String DATA_URL_PREFIX = "data:";

    protected final LlmSectionAttributes attributes;

    private final int[] keys;

    private int depth;
    private boolean resolved;
    private boolean rejected;

    protected LlmAttributeProbe()
    {
        this.attributes = new LlmSectionAttributes();
        this.keys = new int[MAX_DEPTH];
    }

    final void begin(
        String type,
        String blockType)
    {
        attributes.clear();
        depth = 1;
        resolved = false;
        rejected = false;
        keys[1] = NO_KEY;

        onBegin(type, blockType);
    }

    final LlmSectionAttributes attributes()
    {
        return attributes;
    }

    final int depth()
    {
        return depth;
    }

    final Status onEvent(
        JsonEvent event,
        JsonParserEx parser)
    {
        switch (event)
        {
        case START_OBJECT:
        case START_ARRAY:
            depth++;
            onStart(depth);
            if (depth < MAX_DEPTH)
            {
                keys[depth] = NO_KEY;
            }
            break;
        case END_OBJECT:
        case END_ARRAY:
            depth--;
            break;
        case KEY_NAME:
            if (!parser.deferredBytes() && depth < MAX_DEPTH)
            {
                keys[depth] = onKey(depth, parser.getStringView());
            }
            break;
        case VALUE_STRING:
            final boolean deferred = parser.deferredBytes();
            final CharSequence value = parser.getStringView();
            final int length = value.length();
            if (depth < MAX_DEPTH)
            {
                onString(depth, value, deferred);
            }
            if (deferred)
            {
                parser.consumed(length);
            }
            break;
        default:
            break;
        }

        return rejected ? Status.REJECTED
            : depth == 0 ? Status.COMPLETE
            : resolved ? Status.RESOLVED
            : Status.PENDING;
    }

    protected abstract void onBegin(
        String type,
        String blockType);

    protected abstract int onKey(
        int depth,
        CharSequence key);

    protected void onStart(
        int depth)
    {
    }

    protected abstract void onString(
        int depth,
        CharSequence value,
        boolean deferred);

    protected final int key(
        int depth)
    {
        return depth > 0 && depth < MAX_DEPTH ? keys[depth] : NO_KEY;
    }

    protected final void resolve()
    {
        resolved = true;
    }

    protected final void reject()
    {
        rejected = true;
    }

    protected final void mediaUrl(
        CharSequence value,
        boolean deferred)
    {
        if (startsWith(value, DATA_URL_PREFIX))
        {
            attributes.inline(mediaTypeOf(value));
        }
        else if (deferred)
        {
            reject();
        }
        else
        {
            attributes.url(value.toString());
        }
    }

    protected final void fileData(
        CharSequence value)
    {
        attributes.inline(startsWith(value, DATA_URL_PREFIX) ? mediaTypeOf(value) : null);
    }

    protected static boolean matches(
        CharSequence value,
        String expected)
    {
        final int length = expected.length();
        boolean matches = value.length() == length;

        for (int index = 0; matches && index < length; index++)
        {
            matches = value.charAt(index) == expected.charAt(index);
        }

        return matches;
    }

    private static boolean startsWith(
        CharSequence value,
        String prefix)
    {
        final int length = prefix.length();
        boolean starts = value.length() >= length;

        for (int index = 0; starts && index < length; index++)
        {
            starts = value.charAt(index) == prefix.charAt(index);
        }

        return starts;
    }

    private static String mediaTypeOf(
        CharSequence dataUrl)
    {
        final int from = DATA_URL_PREFIX.length();
        int to = from;

        while (to < dataUrl.length() && dataUrl.charAt(to) != ';' && dataUrl.charAt(to) != ',')
        {
            to++;
        }

        return to > from && to < dataUrl.length() ? dataUrl.subSequence(from, to).toString() : null;
    }
}
