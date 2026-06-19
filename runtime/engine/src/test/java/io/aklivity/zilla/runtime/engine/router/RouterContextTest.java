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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.binding.BindingHandler;
import io.aklivity.zilla.runtime.engine.config.RouterConfig;

public class RouterContextTest
{
    private final RouterContext context = new RouterContext()
    {
        @Override
        public BindingHandler attach(
            RouterConfig config)
        {
            return null;
        }

        @Override
        public void detach(
            long routerId)
        {
        }
    };

    @Test
    public void shouldResolveAffinityUnchangedByDefault() throws Exception
    {
        long affinity = 0x0000_0001_dead_beefL;

        assertEquals(affinity, context.affinity(0L, affinity));
    }

    @Test
    public void shouldTreatAffinityAsLocalByDefault() throws Exception
    {
        assertTrue(context.isLocalAffinity(0L, 0x0000_0001_dead_beefL));
    }
}
