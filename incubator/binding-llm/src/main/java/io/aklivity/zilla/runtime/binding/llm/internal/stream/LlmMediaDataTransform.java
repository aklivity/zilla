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

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;
import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSource;
import io.aklivity.zilla.runtime.common.json.JsonTransform;

abstract class LlmMediaDataTransform implements JsonTransform
{
    private static final String DATA_URL_PREFIX = "data:";
    private static final String BASE64_MARKER = ";base64,";
    private static final String KEY_TYPE = "type";

    private static final int NO_CONTAINER = -1;

    private final String[] containers;
    private final String[] members;
    private final boolean dataUrl;
    private final String type;

    private int depth;
    private int container;
    private boolean memberNext;
    private boolean typeNext;
    private boolean opened;
    private boolean found;

    protected LlmMediaDataTransform(
        String[] containers,
        String[] members,
        boolean dataUrl,
        String type)
    {
        this.containers = containers;
        this.members = members;
        this.dataUrl = dataUrl;
        this.type = type;
        this.container = NO_CONTAINER;
    }

    @Override
    public Status transform(
        JsonController control,
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        Status status = Status.ADVANCED;

        switch (event)
        {
        case START_DOCUMENT:
            status = sink.transform(control, source, event);
            break;
        case END_DOCUMENT:
            status = found ? sink.transform(control, source, event) : Status.REJECTED;
            break;
        case START_OBJECT:
        case START_ARRAY:
            depth++;
            memberNext = false;
            typeNext = false;
            break;
        case END_OBJECT:
        case END_ARRAY:
            depth--;
            if (depth == 1)
            {
                container = NO_CONTAINER;
            }
            break;
        case KEY_NAME:
            onKey(source);
            break;
        case VALUE_STRING:
            status = onString(control, source, event, sink);
            break;
        default:
            memberNext = false;
            typeNext = false;
            break;
        }

        return status;
    }

    @Override
    public boolean deterministic()
    {
        return true;
    }

    @Override
    public void reset()
    {
        depth = 0;
        container = NO_CONTAINER;
        memberNext = false;
        typeNext = false;
        opened = false;
        found = false;
    }

    private void onKey(
        JsonSource source)
    {
        memberNext = false;
        typeNext = false;

        if (!source.deferredBytes())
        {
            final CharSequence key = source.getStringView();

            if (depth == 1)
            {
                container = NO_CONTAINER;

                for (int index = 0; index < containers.length && container == NO_CONTAINER; index++)
                {
                    if (matches(key, containers[index]))
                    {
                        container = index;
                    }
                }
            }
            else if (depth == 2 && container != NO_CONTAINER)
            {
                memberNext = matches(key, members[container]);
                typeNext = type != null && matches(key, KEY_TYPE);
            }
        }
    }

    private Status onString(
        JsonController control,
        JsonSource source,
        JsonEvent event,
        JsonSink sink)
    {
        final boolean deferred = source.deferredBytes();
        final CharSequence view = source.getStringView();
        Status status = Status.ADVANCED;

        if (memberNext && depth == 2)
        {
            status = onData(control, source, event, sink, deferred, view);
        }
        else if (typeNext && depth == 2)
        {
            typeNext = false;
            status = !deferred && matches(view, type) ? Status.ADVANCED : Status.REJECTED;
        }
        else if (deferred)
        {
            control.consumed(view.length());
            status = Status.STARVED;
        }

        return status;
    }

    private Status onData(
        JsonController control,
        JsonSource source,
        JsonEvent event,
        JsonSink sink,
        boolean deferred,
        CharSequence view)
    {
        Status status = Status.ADVANCED;

        if (!opened)
        {
            if (dataUrl)
            {
                status = skipPrefix(control, deferred, view);
            }

            opened = status == Status.ADVANCED;
        }

        if (opened)
        {
            status = sink.transform(control, source, event);

            if (!deferred)
            {
                memberNext = false;
                opened = false;
                found = true;
            }
        }

        return status;
    }

    private Status skipPrefix(
        JsonController control,
        boolean deferred,
        CharSequence view)
    {
        Status status = Status.REJECTED;

        final DirectBufferEx mediaType = control.envelope().get(LlmSectionAttributes.MEDIA_TYPE, 0);

        if (mediaType != null)
        {
            final int prefixLength = DATA_URL_PREFIX.length() + mediaType.capacity() + BASE64_MARKER.length();

            if (view.length() >= prefixLength)
            {
                if (prefixMatches(view, mediaType))
                {
                    control.consumed(prefixLength);
                    status = Status.ADVANCED;
                }
            }
            else if (deferred)
            {
                control.consumed(0);
                status = Status.STARVED;
            }
        }

        return status;
    }

    private static boolean prefixMatches(
        CharSequence view,
        DirectBufferEx mediaType)
    {
        final int mediaTypeLength = mediaType.capacity();
        boolean matches = startsWith(view, 0, DATA_URL_PREFIX);

        for (int index = 0; matches && index < mediaTypeLength; index++)
        {
            matches = view.charAt(DATA_URL_PREFIX.length() + index) == (char) mediaType.getByte(index);
        }

        return matches && startsWith(view, DATA_URL_PREFIX.length() + mediaTypeLength, BASE64_MARKER);
    }

    private static boolean startsWith(
        CharSequence view,
        int offset,
        String prefix)
    {
        boolean matches = true;

        for (int index = 0; matches && index < prefix.length(); index++)
        {
            matches = view.charAt(offset + index) == prefix.charAt(index);
        }

        return matches;
    }

    private static boolean matches(
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
}
