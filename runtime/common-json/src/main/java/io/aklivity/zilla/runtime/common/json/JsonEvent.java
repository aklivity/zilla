/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.common.json;

import jakarta.json.stream.JsonParser.Event;

/**
 * The event currency of a {@link JsonStream} pipeline. A superset of the structured events of
 * {@code jakarta.json.stream.JsonParser.Event} (which cannot be extended) plus document framing
 * ({@link #START_DOCUMENT} / {@link #END_DOCUMENT}) and a single {@link #SEGMENT} event. A segment run
 * delivers one complete value as raw bytes rather than as structured events, one {@link #SEGMENT} per
 * fragment; {@link JsonSource#deferredBytes()} is {@code true} on every fragment but the last.
 * {@link #segmented()} distinguishes the segment event.
 */
public enum JsonEvent
{
    START_DOCUMENT,
    END_DOCUMENT,
    START_OBJECT,
    END_OBJECT,
    START_ARRAY,
    END_ARRAY,
    KEY_NAME,
    VALUE_STRING,
    VALUE_NUMBER,
    VALUE_TRUE,
    VALUE_FALSE,
    VALUE_NULL,
    SEGMENT,
    VERBATIM;

    public boolean segmented()
    {
        return this == SEGMENT;
    }

    /**
     * Whether this event carries a coalesced run of original source bytes pulled via
     * {@link JsonSource#getVerbatim(int)}. A {@code VERBATIM} event is <em>not</em> {@link #segmented()}: it
     * rides alongside the structured event stream (additive) rather than substituting for it, so a consumer
     * can both inspect structure and reproduce the input verbatim.
     */
    public boolean isVerbatim()
    {
        return this == VERBATIM;
    }

    public static JsonEvent of(
        Event event)
    {
        return switch (event)
        {
        case START_OBJECT -> START_OBJECT;
        case END_OBJECT -> END_OBJECT;
        case START_ARRAY -> START_ARRAY;
        case END_ARRAY -> END_ARRAY;
        case KEY_NAME -> KEY_NAME;
        case VALUE_STRING -> VALUE_STRING;
        case VALUE_NUMBER -> VALUE_NUMBER;
        case VALUE_TRUE -> VALUE_TRUE;
        case VALUE_FALSE -> VALUE_FALSE;
        case VALUE_NULL -> VALUE_NULL;
        };
    }
}
