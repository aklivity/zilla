/*
 * Copyright 2021-2024 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.guard;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.test.internal.guard.TestGuard;

public final class GuardFactoryTest
{
    @Test
    public void shouldLoadAndCreate()
    {
        Configuration config = new Configuration();
        GuardFactory factory = GuardFactory.instantiate();
        Guard guard = factory.create("test", config);

        assertThat(guard, instanceOf(TestGuard.class));
    }

    @Test
    public void shouldReturnNullElicitationByDefault()
    {
        GuardHandler handler = new GuardHandler()
        {
            @Override
            public long reauthorize(long traceId, long bindingId, long contextId, String credentials)
            {
                return NOT_AUTHORIZED;
            }

            @Override
            public void deauthorize(long sessionId)
            {
            }

            @Override
            public String identity(long sessionId)
            {
                return null;
            }

            @Override
            public String attribute(long sessionId, String name)
            {
                return null;
            }

            @Override
            public String credentials(long sessionId)
            {
                return null;
            }

            @Override
            public long expiresAt(long sessionId)
            {
                return EXPIRES_NEVER;
            }

            @Override
            public long expiringAt(long sessionId)
            {
                return EXPIRES_NEVER;
            }

            @Override
            public boolean challenge(long sessionId, long now)
            {
                return false;
            }
        };

        assertThat(handler.elicitation(0L, 0L, 0L, null), nullValue());
    }
}
