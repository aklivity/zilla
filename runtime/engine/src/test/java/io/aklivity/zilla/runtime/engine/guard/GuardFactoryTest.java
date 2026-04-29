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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import org.agrona.collections.MutableLong;
import org.junit.Test;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.config.GuardConfig;
import io.aklivity.zilla.runtime.engine.test.internal.guard.TestGuard;
import io.aklivity.zilla.runtime.engine.test.internal.guard.TestGuardConfig;
import io.aklivity.zilla.runtime.engine.test.internal.guard.TestGuardHandler;

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
    public void shouldReturnNullPreauthorizeByDefault()
    {
        GuardConfig config = GuardConfig.builder().namespace("test").name("test").type("test").build();
        GuardHandler handler = new TestGuardHandler(new TestGuardConfig(config));

        assertThat(handler.preauthorize(0L, 0L, 0L, null), nullValue());
    }

    @Test
    public void shouldDelegateAsyncReauthorizeToSyncByDefault()
    {
        GuardConfig config = GuardConfig.builder().namespace("test").name("test").type("test").build();
        GuardHandler handler = new TestGuardHandler(new TestGuardConfig(config));

        MutableLong result = new MutableLong(Long.MIN_VALUE);
        handler.reauthorize(0L, 0L, 0L, null, result::set);

        assertThat(result.value, equalTo(handler.reauthorize(0L, 0L, 0L, null)));
    }
}
