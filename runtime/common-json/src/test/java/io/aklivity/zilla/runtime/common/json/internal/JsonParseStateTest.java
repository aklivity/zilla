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
package io.aklivity.zilla.runtime.common.json.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class JsonParseStateTest
{
    @Test
    public void shouldHoldEveryLevelAsAnArrayAtTheDepthLimit()
    {
        long path = 0L;
        for (int depth = 0; depth < JsonParseState.MAX_DEPTH; depth++)
        {
            path = JsonParseState.pushPath(path, depth, true);
        }

        for (int level = 0; level < JsonParseState.MAX_DEPTH; level++)
        {
            assertTrue(JsonParseState.pathArrayAt(path, level));
        }
        assertEquals(-1L, path);
        assertTrue(JsonParseState.pathInArray(path, JsonParseState.MAX_DEPTH));
        assertFalse(JsonParseState.pathInObject(path, JsonParseState.MAX_DEPTH));
    }

    @Test
    public void shouldHoldEveryLevelAsAnObjectAtTheDepthLimit()
    {
        long path = -1L;
        for (int depth = 0; depth < JsonParseState.MAX_DEPTH; depth++)
        {
            path = JsonParseState.pushPath(path, depth, false);
        }

        assertEquals(0L, path);
        assertTrue(JsonParseState.pathInObject(path, JsonParseState.MAX_DEPTH));
        assertFalse(JsonParseState.pathInArray(path, JsonParseState.MAX_DEPTH));
    }

    @Test
    public void shouldReadBackEachLevelOfAnAlternatingPath()
    {
        long path = 0L;
        for (int depth = 0; depth < JsonParseState.MAX_DEPTH; depth++)
        {
            path = JsonParseState.pushPath(path, depth, depth % 3 == 0);
        }

        for (int level = 0; level < JsonParseState.MAX_DEPTH; level++)
        {
            assertEquals(level % 3 == 0, JsonParseState.pathArrayAt(path, level));
        }
    }

    @Test
    public void shouldPopEachLevelBackToTheEmptyPath()
    {
        long path = 0L;
        for (int depth = 0; depth < JsonParseState.MAX_DEPTH; depth++)
        {
            path = JsonParseState.pushPath(path, depth, depth % 2 == 0);
        }

        for (int depth = JsonParseState.MAX_DEPTH; depth > 0; depth--)
        {
            assertEquals((depth - 1) % 2 == 0, JsonParseState.pathInArray(path, depth));
            path = JsonParseState.popPath(path, depth);
            assertEquals(0L, path >>> depth - 1);
        }

        assertEquals(0L, path);
    }

    @Test
    public void shouldOverwriteALevelLeftByAnEarlierPush()
    {
        long path = JsonParseState.pushPath(0L, 0, true);
        path = JsonParseState.popPath(path, 1);
        path = JsonParseState.pushPath(path, 0, false);

        assertFalse(JsonParseState.pathArrayAt(path, 0));
        assertEquals(0L, path);
    }

    @Test
    public void shouldReportNoContainerAtDepthZero()
    {
        assertFalse(JsonParseState.pathInArray(-1L, 0));
        assertFalse(JsonParseState.pathInObject(-1L, 0));
    }

    @Test
    public void shouldRejectAPushPastTheDepthLimit()
    {
        long path = 0L;
        for (int depth = 0; depth < JsonParseState.MAX_DEPTH; depth++)
        {
            path = JsonParseState.pushPath(path, depth, true);
        }

        assertTrue(JsonParseState.pathFull(JsonParseState.MAX_DEPTH));
        assertFalse(JsonParseState.pathFull(JsonParseState.MAX_DEPTH - 1));
        final long full = path;
        assertThrows(IllegalArgumentException.class, () -> JsonParseState.pushPath(full, JsonParseState.MAX_DEPTH, false));
    }

    @Test
    public void shouldRejectAPopFromTheEmptyPath()
    {
        assertThrows(IllegalArgumentException.class, () -> JsonParseState.popPath(0L, 0));
    }
}
