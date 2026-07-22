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
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.aklivity.zilla.config.guard.inline.InlineOptionsConfig;

public class InlineGuardHandlerTest
{
    @Test
    public void shouldVerifyRolesForSession()
    {
        InlineGuardHandler handler = new InlineGuardHandler(() -> 1L, null);

        long sessionId = handler.reauthorize(0L, 0L, 0L, "user");

        assertTrue(handler.verify(sessionId, null));
        assertTrue(handler.verify(sessionId, emptyList()));
        assertFalse(handler.verify(sessionId, of("admin")));
        assertFalse(handler.verify(0L, emptyList()));
    }

    @Test
    public void shouldSplitIdentityAndCredentialsWhenFormatConfigured()
    {
        InlineOptionsConfig options = new InlineOptionsConfig(null, null, "{identity}:{credentials}");
        InlineGuardHandler handler = new InlineGuardHandler(() -> 1L, options);

        long sessionId = handler.reauthorize(0L, 0L, 0L, "alice:secret");

        assertThat(handler.identity(sessionId), equalTo("alice"));
        assertThat(handler.credentials(sessionId), equalTo("secret"));
    }

    @Test
    public void shouldFallBackToWholeValueAsIdentityWhenFormatConfiguredButInputDoesNotMatch()
    {
        InlineOptionsConfig options = new InlineOptionsConfig(null, "default-credentials", "{identity}:{credentials}");
        InlineGuardHandler handler = new InlineGuardHandler(() -> 1L, options);

        long sessionId = handler.reauthorize(0L, 0L, 0L, "malformed-input-without-separator");

        assertThat(handler.identity(sessionId), equalTo("malformed-input-without-separator"));
        assertThat(handler.credentials(sessionId), equalTo("default-credentials"));
    }

    @Test
    public void shouldFallBackToNullCredentialsWhenFormatConfiguredButInputDoesNotMatchAndNoStaticDefault()
    {
        InlineOptionsConfig options = new InlineOptionsConfig(null, null, "{identity}:{credentials}");
        InlineGuardHandler handler = new InlineGuardHandler(() -> 1L, options);

        long sessionId = handler.reauthorize(0L, 0L, 0L, "malformed-input-without-separator");

        assertThat(handler.identity(sessionId), equalTo("malformed-input-without-separator"));
        assertThat(handler.credentials(sessionId), nullValue());
    }

    @Test
    public void shouldReturnSameValueForIdentityAndCredentialsWhenFormatNotConfigured()
    {
        InlineGuardHandler handler = new InlineGuardHandler(() -> 1L, null);

        long sessionId = handler.reauthorize(0L, 0L, 0L, "alice:secret");

        assertThat(handler.identity(sessionId), equalTo("alice:secret"));
        assertThat(handler.credentials(sessionId), equalTo("alice:secret"));
    }
}
