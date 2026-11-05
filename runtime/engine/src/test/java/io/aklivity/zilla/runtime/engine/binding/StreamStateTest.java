/*
 * Copyright 2021-2026 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.engine.binding;

import static io.aklivity.zilla.runtime.engine.binding.StreamState.abortingInitial;
import static io.aklivity.zilla.runtime.engine.binding.StreamState.abortingReply;
import static io.aklivity.zilla.runtime.engine.binding.StreamState.closeInitial;
import static io.aklivity.zilla.runtime.engine.binding.StreamState.closeReply;
import static io.aklivity.zilla.runtime.engine.binding.StreamState.closed;
import static io.aklivity.zilla.runtime.engine.binding.StreamState.closingInitial;
import static io.aklivity.zilla.runtime.engine.binding.StreamState.closingReply;
import static io.aklivity.zilla.runtime.engine.binding.StreamState.initialAborting;
import static io.aklivity.zilla.runtime.engine.binding.StreamState.initialClosed;
import static io.aklivity.zilla.runtime.engine.binding.StreamState.initialClosing;
import static io.aklivity.zilla.runtime.engine.binding.StreamState.initialOpened;
import static io.aklivity.zilla.runtime.engine.binding.StreamState.initialOpening;
import static io.aklivity.zilla.runtime.engine.binding.StreamState.openInitial;
import static io.aklivity.zilla.runtime.engine.binding.StreamState.openReply;
import static io.aklivity.zilla.runtime.engine.binding.StreamState.openingInitial;
import static io.aklivity.zilla.runtime.engine.binding.StreamState.openingReply;
import static io.aklivity.zilla.runtime.engine.binding.StreamState.replyAborting;
import static io.aklivity.zilla.runtime.engine.binding.StreamState.replyClosed;
import static io.aklivity.zilla.runtime.engine.binding.StreamState.replyClosing;
import static io.aklivity.zilla.runtime.engine.binding.StreamState.replyOpened;
import static io.aklivity.zilla.runtime.engine.binding.StreamState.replyOpening;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StreamStateTest
{
    @Test
    public void shouldOpenInitialAsOpeningAndOpened()
    {
        final int state = openInitial(0);

        assertTrue(initialOpening(state));
        assertTrue(initialOpened(state));
        assertFalse(replyOpening(state));
        assertFalse(replyOpened(state));
    }

    @Test
    public void shouldOpeningInitialWithoutOpened()
    {
        final int state = openingInitial(0);

        assertTrue(initialOpening(state));
        assertFalse(initialOpened(state));
    }

    @Test
    public void shouldOpenReplyWithoutOpeningWhenWindowArrivesBeforeBegin()
    {
        final int state = openReply(0);

        assertTrue(replyOpened(state));
        assertFalse(replyOpening(state));
    }

    @Test
    public void shouldOpeningReplyWithoutOpened()
    {
        final int state = openingReply(0);

        assertTrue(replyOpening(state));
        assertFalse(replyOpened(state));
    }

    @Test
    public void shouldOpenReplyInEitherOrder()
    {
        assertEquals(openReply(openingReply(0)), openingReply(openReply(0)));
    }

    @Test
    public void shouldCloseInitialAsClosingAndClosed()
    {
        final int state = closeInitial(0);

        assertTrue(initialClosing(state));
        assertTrue(initialClosed(state));
        assertFalse(replyClosing(state));
        assertFalse(replyClosed(state));
    }

    @Test
    public void shouldClosingInitialWithoutClosed()
    {
        final int state = closingInitial(0);

        assertTrue(initialClosing(state));
        assertFalse(initialClosed(state));
    }

    @Test
    public void shouldCloseReplyAsClosingAndClosed()
    {
        final int state = closeReply(0);

        assertTrue(replyClosing(state));
        assertTrue(replyClosed(state));
        assertFalse(initialClosing(state));
        assertFalse(initialClosed(state));
    }

    @Test
    public void shouldClosingReplyWithoutClosed()
    {
        final int state = closingReply(0);

        assertTrue(replyClosing(state));
        assertFalse(replyClosed(state));
    }

    @Test
    public void shouldCloseReplyWithoutOpeningWhenAbortedBeforeBegin()
    {
        final int state = closeReply(0);

        assertTrue(replyClosed(state));
        assertFalse(replyOpening(state));
        assertFalse(replyOpened(state));
    }

    @Test
    public void shouldAbortEachDirectionIndependently()
    {
        assertTrue(initialAborting(abortingInitial(0)));
        assertFalse(replyAborting(abortingInitial(0)));
        assertTrue(replyAborting(abortingReply(0)));
        assertFalse(initialAborting(abortingReply(0)));
    }

    @Test
    public void shouldBeClosedOnlyWhenBothDirectionsClosed()
    {
        assertFalse(closed(0));
        assertFalse(closed(closeInitial(0)));
        assertFalse(closed(closeReply(0)));
        assertTrue(closed(closeReply(closeInitial(0))));
    }
}
