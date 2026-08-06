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
package io.aklivity.zilla.runtime.guard.inline.internal;

import static java.util.Collections.emptyList;
import static java.util.List.of;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayDeque;
import java.util.Deque;

import org.junit.Test;

import io.aklivity.zilla.config.guard.inline.InlineOptionsConfig;
import io.aklivity.zilla.runtime.engine.guard.GuardHandler.LongCompletionCallback;

public class InlineGuardHandlerTest
{
    @Test
    public void shouldDeferAsyncReauthorize()
    {
        Deque<Runnable> dispatched = new ArrayDeque<>();
        InlineGuardHandler handler = new InlineGuardHandler(() -> 1L, null, dispatched::offer);

        long[] completed = new long[] { Long.MIN_VALUE, Long.MIN_VALUE };
        LongCompletionCallback completion = new LongCompletionCallback()
        {
            @Override
            public void completed(
                long contextId,
                long sessionId)
            {
                completed[0] = contextId;
                completed[1] = sessionId;
            }

            @Override
            public void failed(
                long contextId,
                Throwable ex)
            {
                throw new AssertionError("unexpected failure", ex);
            }
        };

        handler.reauthorize(0L, 0L, 42L, "user", completion);

        assertThat("completed on the caller's stack", completed[1], equalTo(Long.MIN_VALUE));
        assertThat(dispatched.size(), equalTo(1));

        dispatched.remove().run();

        assertThat(completed[0], equalTo(42L));
        assertTrue(handler.verify(completed[1], emptyList()));
    }

    @Test
    public void shouldFailAsyncReauthorizeWhenCredentialsAbsent()
    {
        Deque<Runnable> dispatched = new ArrayDeque<>();
        InlineOptionsConfig options = InlineOptionsConfig.builder()
            .format("{identity}:{credentials}")
            .build();
        InlineGuardHandler handler = new InlineGuardHandler(() -> 1L, options, dispatched::offer);

        Throwable[] failure = new Throwable[1];
        LongCompletionCallback completion = new LongCompletionCallback()
        {
            @Override
            public void completed(
                long contextId,
                long sessionId)
            {
                throw new AssertionError("unexpected completion");
            }

            @Override
            public void failed(
                long contextId,
                Throwable ex)
            {
                failure[0] = ex;
            }
        };

        handler.reauthorize(0L, 0L, 42L, null, completion);

        // a null credential trips the format matcher inside the synchronous decision; the
        // failure has to reach the caller through failed(), and no sooner than the tick
        assertThat("failed on the caller's stack", failure[0], nullValue());

        dispatched.remove().run();

        assertThat(failure[0], instanceOf(NullPointerException.class));
    }

    @Test
    public void shouldVerifyRolesForSession()
    {
        InlineGuardHandler handler = new InlineGuardHandler(() -> 1L, null, Runnable::run);

        long sessionId = handler.reauthorize(0L, 0L, 0L, "user");

        assertTrue(handler.verify(sessionId, null));
        assertTrue(handler.verify(sessionId, emptyList()));
        assertFalse(handler.verify(sessionId, of("admin")));
        assertFalse(handler.verify(0L, emptyList()));
    }

    @Test
    public void shouldSplitIdentityAndCredentialsWhenFormatConfigured()
    {
        InlineOptionsConfig options = InlineOptionsConfig.builder()
            .format("{identity}:{credentials}")
            .build();
        InlineGuardHandler handler = new InlineGuardHandler(() -> 1L, options, Runnable::run);

        long sessionId = handler.reauthorize(0L, 0L, 0L, "alice:secret");

        assertThat(handler.identity(sessionId), equalTo("alice"));
        assertThat(handler.credentials(sessionId), equalTo("secret"));
    }

    @Test
    public void shouldFallBackToWholeValueAsIdentityWhenFormatConfiguredButInputDoesNotMatch()
    {
        InlineOptionsConfig options = InlineOptionsConfig.builder()
            .credentials("default-credentials")
            .format("{identity}:{credentials}")
            .build();
        InlineGuardHandler handler = new InlineGuardHandler(() -> 1L, options, Runnable::run);

        long sessionId = handler.reauthorize(0L, 0L, 0L, "malformed-input-without-separator");

        assertThat(handler.identity(sessionId), equalTo("malformed-input-without-separator"));
        assertThat(handler.credentials(sessionId), equalTo("default-credentials"));
    }

    @Test
    public void shouldFallBackToNullCredentialsWhenFormatConfiguredButInputDoesNotMatchAndNoStaticDefault()
    {
        InlineOptionsConfig options = InlineOptionsConfig.builder()
            .format("{identity}:{credentials}")
            .build();
        InlineGuardHandler handler = new InlineGuardHandler(() -> 1L, options, Runnable::run);

        long sessionId = handler.reauthorize(0L, 0L, 0L, "malformed-input-without-separator");

        assertThat(handler.identity(sessionId), equalTo("malformed-input-without-separator"));
        assertThat(handler.credentials(sessionId), nullValue());
    }

    @Test
    public void shouldReturnSameValueForIdentityAndCredentialsWhenFormatNotConfigured()
    {
        InlineGuardHandler handler = new InlineGuardHandler(() -> 1L, null, Runnable::run);

        long sessionId = handler.reauthorize(0L, 0L, 0L, "alice:secret");

        assertThat(handler.identity(sessionId), equalTo("alice:secret"));
        assertThat(handler.credentials(sessionId), equalTo("alice:secret"));
    }
}
