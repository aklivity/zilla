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
package io.aklivity.zilla.runtime.binding.a2a.http.internal.stream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class A2aHttpStateTest
{
    @Test
    public void shouldTrackInitialLifecycle()
    {
        int state = 0;
        assertFalse(A2aHttpState.initialOpening(state));

        state = A2aHttpState.openingInitial(state);
        assertTrue(A2aHttpState.initialOpening(state));
        assertFalse(A2aHttpState.initialOpened(state));

        state = A2aHttpState.openInitial(state);
        assertTrue(A2aHttpState.initialOpened(state));

        state = A2aHttpState.closingInitial(state);
        assertTrue(A2aHttpState.initialClosing(state));
        assertFalse(A2aHttpState.initialClosed(state));

        state = A2aHttpState.closeInitial(state);
        assertTrue(A2aHttpState.initialClosed(state));
    }

    @Test
    public void shouldTrackReplyLifecycle()
    {
        int state = 0;
        assertFalse(A2aHttpState.replyOpening(state));

        state = A2aHttpState.openReply(state);
        assertTrue(A2aHttpState.replyOpening(state));
        assertTrue(A2aHttpState.replyOpened(state));

        state = A2aHttpState.closingReply(state);
        assertTrue(A2aHttpState.replyClosing(state));
        assertFalse(A2aHttpState.replyClosed(state));

        state = A2aHttpState.closeReply(state);
        assertTrue(A2aHttpState.replyClosed(state));
    }
}
