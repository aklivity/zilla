/*
 * Copyright 2021-2022 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.filesystem.internal.stream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FileSystemStateTest
{
    @Test
    public void shouldTransitionOpeningInitial()
    {
        int state = FileSystemState.openingInitial(0);

        assertTrue(FileSystemState.initialOpening(state));
        assertFalse(FileSystemState.initialOpened(state));
        assertFalse(FileSystemState.initialClosing(state));
        assertFalse(FileSystemState.initialClosed(state));
        assertFalse(FileSystemState.replyOpening(state));
        assertFalse(FileSystemState.replyOpened(state));
        assertFalse(FileSystemState.replyClosing(state));
        assertFalse(FileSystemState.replyClosed(state));
    }

    @Test
    public void shouldTransitionOpenInitial()
    {
        int state = FileSystemState.openInitial(0);

        assertTrue(FileSystemState.initialOpening(state));
        assertTrue(FileSystemState.initialOpened(state));
        assertFalse(FileSystemState.initialClosing(state));
        assertFalse(FileSystemState.initialClosed(state));
        assertFalse(FileSystemState.replyOpening(state));
        assertFalse(FileSystemState.replyOpened(state));
        assertFalse(FileSystemState.replyClosing(state));
        assertFalse(FileSystemState.replyClosed(state));
    }

    @Test
    public void shouldTransitionClosingInitial()
    {
        int state = FileSystemState.closingInitial(0);

        assertFalse(FileSystemState.initialOpening(state));
        assertFalse(FileSystemState.initialOpened(state));
        assertTrue(FileSystemState.initialClosing(state));
        assertFalse(FileSystemState.initialClosed(state));
        assertFalse(FileSystemState.replyOpening(state));
        assertFalse(FileSystemState.replyOpened(state));
        assertFalse(FileSystemState.replyClosing(state));
        assertFalse(FileSystemState.replyClosed(state));
    }

    @Test
    public void shouldTransitionCloseInitial()
    {
        int state = FileSystemState.closeInitial(0);

        assertFalse(FileSystemState.initialOpening(state));
        assertFalse(FileSystemState.initialOpened(state));
        assertTrue(FileSystemState.initialClosing(state));
        assertTrue(FileSystemState.initialClosed(state));
        assertFalse(FileSystemState.replyOpening(state));
        assertFalse(FileSystemState.replyOpened(state));
        assertFalse(FileSystemState.replyClosing(state));
        assertFalse(FileSystemState.replyClosed(state));
    }

    @Test
    public void shouldTransitionOpeningReply()
    {
        int state = FileSystemState.openingReply(0);

        assertFalse(FileSystemState.initialOpening(state));
        assertFalse(FileSystemState.initialOpened(state));
        assertFalse(FileSystemState.initialClosing(state));
        assertFalse(FileSystemState.initialClosed(state));
        assertTrue(FileSystemState.replyOpening(state));
        assertFalse(FileSystemState.replyOpened(state));
        assertFalse(FileSystemState.replyClosing(state));
        assertFalse(FileSystemState.replyClosed(state));
    }

    @Test
    public void shouldTransitionOpenReply()
    {
        int state = FileSystemState.openReply(0);

        assertFalse(FileSystemState.initialOpening(state));
        assertFalse(FileSystemState.initialOpened(state));
        assertFalse(FileSystemState.initialClosing(state));
        assertFalse(FileSystemState.initialClosed(state));
        assertTrue(FileSystemState.replyOpening(state));
        assertTrue(FileSystemState.replyOpened(state));
        assertFalse(FileSystemState.replyClosing(state));
        assertFalse(FileSystemState.replyClosed(state));
    }

    @Test
    public void shouldTransitionClosingReply()
    {
        int state = FileSystemState.closingReply(0);

        assertFalse(FileSystemState.initialOpening(state));
        assertFalse(FileSystemState.initialOpened(state));
        assertFalse(FileSystemState.initialClosing(state));
        assertFalse(FileSystemState.initialClosed(state));
        assertFalse(FileSystemState.replyOpening(state));
        assertFalse(FileSystemState.replyOpened(state));
        assertTrue(FileSystemState.replyClosing(state));
        assertFalse(FileSystemState.replyClosed(state));
    }

    @Test
    public void shouldTransitionCloseReply()
    {
        int state = FileSystemState.closeReply(0);

        assertFalse(FileSystemState.initialOpening(state));
        assertFalse(FileSystemState.initialOpened(state));
        assertFalse(FileSystemState.initialClosing(state));
        assertFalse(FileSystemState.initialClosed(state));
        assertFalse(FileSystemState.replyOpening(state));
        assertFalse(FileSystemState.replyOpened(state));
        assertTrue(FileSystemState.replyClosing(state));
        assertTrue(FileSystemState.replyClosed(state));
    }
}
