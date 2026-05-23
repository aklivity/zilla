/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.guard.identity.internal;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;

import java.net.URL;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.guard.Guard;
import io.aklivity.zilla.runtime.engine.guard.GuardContext;
import io.aklivity.zilla.runtime.engine.guard.GuardFactory;

public class IdentityGuardFactoryTest
{
    @Test
    public void shouldLoadAndCreate()
    {
        // GIVEN
        Configuration config = new Configuration();
        GuardFactory factory = GuardFactory.instantiate();

        // WHEN
        Guard guard = factory.create("identity", config);
        GuardContext context = guard.supply(mock(EngineContext.class));

        // THEN
        assertThat(guard, instanceOf(IdentityGuard.class));
        assertThat(guard.name(), equalTo("identity"));
        assertThat(guard.type(), instanceOf(URL.class));
        assertThat(context, instanceOf(GuardContext.class));
    }
}
