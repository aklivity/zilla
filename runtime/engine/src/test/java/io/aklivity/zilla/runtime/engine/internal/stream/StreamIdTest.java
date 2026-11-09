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
package io.aklivity.zilla.runtime.engine.internal.stream;

import static io.aklivity.zilla.runtime.engine.internal.stream.StreamId.abortedState;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.binding.StreamState;

public class StreamIdTest
{
    private static final long INITIAL_ID = 1L;
    private static final long REPLY_ID = 2L;

    @Test
    public void shouldAbortInitialWithoutOpeningReply()
    {
        final int state = abortedState(INITIAL_ID);

        assertThat(StreamState.initialOpened(state), equalTo(true));
        assertThat(StreamState.initialAborting(state), equalTo(true));
        assertThat(StreamState.initialClosed(state), equalTo(true));
        assertThat(StreamState.replyOpening(state), equalTo(false));
    }

    @Test
    public void shouldAbortReplyWithoutClosingInitial()
    {
        final int state = abortedState(REPLY_ID);

        assertThat(StreamState.replyOpening(state), equalTo(true));
        assertThat(StreamState.replyAborting(state), equalTo(true));
        assertThat(StreamState.replyClosed(state), equalTo(true));
        assertThat(StreamState.initialClosed(state), equalTo(false));
    }
}
