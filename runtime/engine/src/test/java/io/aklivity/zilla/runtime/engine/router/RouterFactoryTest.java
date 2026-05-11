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
package io.aklivity.zilla.runtime.engine.router;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.Configuration;

public class RouterFactoryTest
{
    @Test
    public void shouldDiscoverRegisteredRouterNames() throws Exception
    {
        RouterFactory factory = RouterFactory.instantiate();

        assertThat(factory.names(), containsInAnyOrder("engine", "test"));
    }

    @Test
    public void shouldCreateRegisteredRouter() throws Exception
    {
        RouterFactory factory = RouterFactory.instantiate();

        Router router = factory.create("test", new Configuration());

        assertNotNull(router);
    }

    @Test
    public void shouldCreateDefaultEngineRouter() throws Exception
    {
        RouterFactory factory = RouterFactory.instantiate();

        Router router = factory.create("engine", new Configuration());

        assertNotNull(router);
    }

    @Test
    public void shouldRejectUnrecognizedRouterNameCitingAvailableNames() throws Exception
    {
        RouterFactory factory = RouterFactory.instantiate();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> factory.create("unknown", new Configuration()));

        assertThat(error.getMessage(), containsString("unknown"));
        assertThat(error.getMessage(), containsString("engine"));
        assertThat(error.getMessage(), containsString("test"));
    }
}
