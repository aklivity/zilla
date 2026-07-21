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
package io.aklivity.zilla.runtime.common.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.json.stream.JsonParser.Event;

import org.junit.jupiter.api.Test;

class JsonEventTest
{
    @Test
    void shouldMapEveryStructuredEvent()
    {
        assertEquals(JsonEvent.START_OBJECT, JsonEvent.of(Event.START_OBJECT));
        assertEquals(JsonEvent.END_OBJECT, JsonEvent.of(Event.END_OBJECT));
        assertEquals(JsonEvent.START_ARRAY, JsonEvent.of(Event.START_ARRAY));
        assertEquals(JsonEvent.END_ARRAY, JsonEvent.of(Event.END_ARRAY));
        assertEquals(JsonEvent.KEY_NAME, JsonEvent.of(Event.KEY_NAME));
        assertEquals(JsonEvent.VALUE_STRING, JsonEvent.of(Event.VALUE_STRING));
        assertEquals(JsonEvent.VALUE_NUMBER, JsonEvent.of(Event.VALUE_NUMBER));
        assertEquals(JsonEvent.VALUE_TRUE, JsonEvent.of(Event.VALUE_TRUE));
        assertEquals(JsonEvent.VALUE_FALSE, JsonEvent.of(Event.VALUE_FALSE));
        assertEquals(JsonEvent.VALUE_NULL, JsonEvent.of(Event.VALUE_NULL));
    }

    @Test
    void shouldFlagSegmentEventAsSegmented()
    {
        assertTrue(JsonEvent.SEGMENT.segmented());
    }

    @Test
    void shouldNotFlagStructuredOrDocumentEventsAsSegmented()
    {
        assertFalse(JsonEvent.START_DOCUMENT.segmented());
        assertFalse(JsonEvent.END_DOCUMENT.segmented());
        assertFalse(JsonEvent.START_OBJECT.segmented());
        assertFalse(JsonEvent.END_OBJECT.segmented());
        assertFalse(JsonEvent.START_ARRAY.segmented());
        assertFalse(JsonEvent.END_ARRAY.segmented());
        assertFalse(JsonEvent.KEY_NAME.segmented());
        assertFalse(JsonEvent.VALUE_STRING.segmented());
        assertFalse(JsonEvent.VALUE_NUMBER.segmented());
        assertFalse(JsonEvent.VALUE_TRUE.segmented());
        assertFalse(JsonEvent.VALUE_FALSE.segmented());
        assertFalse(JsonEvent.VALUE_NULL.segmented());
    }
}
