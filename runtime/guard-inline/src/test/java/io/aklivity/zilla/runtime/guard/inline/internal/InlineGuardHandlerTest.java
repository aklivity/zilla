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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

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
}
