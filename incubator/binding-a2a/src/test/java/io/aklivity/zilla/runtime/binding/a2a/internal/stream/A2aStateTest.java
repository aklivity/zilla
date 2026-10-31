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
package io.aklivity.zilla.runtime.binding.a2a.internal.stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import org.junit.Test;

public class A2aStateTest
{
    @Test
    public void shouldTrackInitialLifecycle()
    {
        int state = 0;

        state = A2aState.openingInitial(state);
        assertThat(A2aState.initialOpening(state), equalTo(true));
        state = A2aState.openInitial(state);
        assertThat(A2aState.initialOpened(state), equalTo(true));
        state = A2aState.closingInitial(state);
        assertThat(A2aState.initialClosing(state), equalTo(true));
        state = A2aState.closeInitial(state);
        assertThat(A2aState.initialClosed(state), equalTo(true));
        assertThat(A2aState.closed(state), equalTo(false));
    }

    @Test
    public void shouldTrackReplyLifecycle()
    {
        int state = 0;

        state = A2aState.openingReply(state);
        assertThat(A2aState.replyOpening(state), equalTo(true));
        state = A2aState.openReply(state);
        assertThat(A2aState.replyOpened(state), equalTo(true));
        state = A2aState.closingReply(state);
        assertThat(A2aState.replyClosing(state), equalTo(true));
        state = A2aState.closeReply(state);
        assertThat(A2aState.replyClosed(state), equalTo(true));
    }

    @Test
    public void shouldImplyOpeningAndClosingWhenOpenedAndClosed()
    {
        assertThat(A2aState.initialOpening(A2aState.openInitial(0)), equalTo(true));
        assertThat(A2aState.initialClosing(A2aState.closeInitial(0)), equalTo(true));
        assertThat(A2aState.replyClosing(A2aState.closeReply(0)), equalTo(true));
    }

    @Test
    public void shouldCloseWhenInitialAndReplyClosed()
    {
        int state = A2aState.closeReply(A2aState.closeInitial(0));

        assertThat(A2aState.closed(state), equalTo(true));
    }
}
