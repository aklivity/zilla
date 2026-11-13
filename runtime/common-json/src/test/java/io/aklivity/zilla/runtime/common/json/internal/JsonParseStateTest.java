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

    @Test
    public void shouldHoldEveryModeFieldAtItsMaximum()
    {
        int mode = JsonParseState.state(0, 15);
        mode = JsonParseState.resumeOp(mode, 7);
        mode = JsonParseState.numberState(mode, 15);
        mode = JsonParseState.literalIndex(mode, 7);
        mode = JsonParseState.memberSeparated(mode, true);
        mode = JsonParseState.separatorBefore(mode, true);
        mode = JsonParseState.segmenting(mode, true);
        mode = JsonParseState.scalarSegment(mode, true);
        mode = JsonParseState.fragmenting(mode, true);
        mode = JsonParseState.escapeArmed(mode, true);
        mode = JsonParseState.lens(mode, true);
        mode = JsonParseState.resumeEscape(mode, true);

        assertEquals(15, JsonParseState.state(mode));
        assertEquals(7, JsonParseState.resumeOp(mode));
        assertEquals(15, JsonParseState.numberState(mode));
        assertEquals(7, JsonParseState.literalIndex(mode));
        assertTrue(JsonParseState.memberSeparated(mode));
        assertTrue(JsonParseState.separatorBefore(mode));
        assertTrue(JsonParseState.segmenting(mode));
        assertTrue(JsonParseState.scalarSegment(mode));
        assertTrue(JsonParseState.fragmenting(mode));
        assertTrue(JsonParseState.escapeArmed(mode));
        assertTrue(JsonParseState.lens(mode));
        assertTrue(JsonParseState.resumeEscape(mode));
    }

    @Test
    public void shouldClearEachModeFieldWithoutDisturbingItsNeighbours()
    {
        int mode = JsonParseState.state(0, 15);
        mode = JsonParseState.resumeOp(mode, 7);
        mode = JsonParseState.numberState(mode, 15);
        mode = JsonParseState.literalIndex(mode, 7);
        mode = JsonParseState.memberSeparated(mode, true);
        mode = JsonParseState.separatorBefore(mode, true);
        mode = JsonParseState.segmenting(mode, true);
        mode = JsonParseState.scalarSegment(mode, true);
        mode = JsonParseState.fragmenting(mode, true);
        mode = JsonParseState.escapeArmed(mode, true);
        mode = JsonParseState.lens(mode, true);
        mode = JsonParseState.resumeEscape(mode, true);

        mode = JsonParseState.state(mode, 0);
        assertEquals(7, JsonParseState.resumeOp(mode));
        mode = JsonParseState.resumeOp(mode, 0);
        assertEquals(15, JsonParseState.numberState(mode));
        mode = JsonParseState.numberState(mode, 0);
        assertEquals(7, JsonParseState.literalIndex(mode));
        mode = JsonParseState.literalIndex(mode, 0);
        assertTrue(JsonParseState.memberSeparated(mode));
        mode = JsonParseState.memberSeparated(mode, false);
        assertTrue(JsonParseState.separatorBefore(mode));
        mode = JsonParseState.separatorBefore(mode, false);
        assertTrue(JsonParseState.segmenting(mode));
        mode = JsonParseState.segmenting(mode, false);
        assertTrue(JsonParseState.scalarSegment(mode));
        mode = JsonParseState.scalarSegment(mode, false);
        assertTrue(JsonParseState.fragmenting(mode));
        mode = JsonParseState.fragmenting(mode, false);
        assertTrue(JsonParseState.escapeArmed(mode));
        mode = JsonParseState.escapeArmed(mode, false);
        assertTrue(JsonParseState.lens(mode));
        mode = JsonParseState.lens(mode, false);
        assertTrue(JsonParseState.resumeEscape(mode));
        mode = JsonParseState.resumeEscape(mode, false);

        assertEquals(0, mode);
    }

    @Test
    public void shouldHoldEveryContextFieldAtItsMaximum()
    {
        int context = JsonParseState.depth(0, JsonParseState.MAX_DEPTH);
        context = JsonParseState.scopeDepth(context, JsonParseState.MAX_DEPTH);
        context = JsonParseState.unicodePending(context, 7);

        assertEquals(JsonParseState.MAX_DEPTH, JsonParseState.depth(context));
        assertEquals(JsonParseState.MAX_DEPTH, JsonParseState.scopeDepth(context));
        assertEquals(7, JsonParseState.unicodePending(context));

        context = JsonParseState.depth(context, 0);
        assertEquals(JsonParseState.MAX_DEPTH, JsonParseState.scopeDepth(context));
        context = JsonParseState.scopeDepth(context, 0);
        assertEquals(7, JsonParseState.unicodePending(context));
        context = JsonParseState.unicodePending(context, 0);
        assertEquals(0, context);
    }

    @Test
    public void shouldHoldEveryParserModeFieldAtItsMaximum()
    {
        int mode = JsonParseState.docState(0, 3);
        mode = JsonParseState.lastEvent(mode, 63);
        mode = JsonParseState.armNextValue(mode, true);
        mode = JsonParseState.verbatim(mode, true);
        mode = JsonParseState.trimLeadingSeparator(mode, true);
        mode = JsonParseState.fragmentOpen(mode, true);

        assertEquals(3, JsonParseState.docState(mode));
        assertEquals(63, JsonParseState.lastEvent(mode));
        assertTrue(JsonParseState.armNextValue(mode));
        assertTrue(JsonParseState.verbatim(mode));
        assertTrue(JsonParseState.trimLeadingSeparator(mode));
        assertTrue(JsonParseState.fragmentOpen(mode));

        mode = JsonParseState.docState(mode, 0);
        assertEquals(63, JsonParseState.lastEvent(mode));
        mode = JsonParseState.lastEvent(mode, 0);
        mode = JsonParseState.armNextValue(mode, false);
        assertTrue(JsonParseState.verbatim(mode));
        mode = JsonParseState.verbatim(mode, false);
        assertTrue(JsonParseState.trimLeadingSeparator(mode));
        mode = JsonParseState.trimLeadingSeparator(mode, false);
        assertTrue(JsonParseState.fragmentOpen(mode));
        mode = JsonParseState.fragmentOpen(mode, false);
        assertEquals(0, mode);
    }
}
