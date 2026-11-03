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
package io.aklivity.zilla.runtime.engine;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

import org.junit.Test;

public class EngineContextTest
{
    @Test
    public void shouldResolveOwnAffinityByDefault()
    {
        EngineContext context = mock(EngineContext.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
        doReturn(0x0000_0000_0100_0001L).when(context).affinity();

        assertThat(context.resolveAffinityId(0L), equalTo(0x0000_0000_0100_0001L));
        assertThat(context.resolveAffinityId(Long.MIN_VALUE), equalTo(0x0000_0000_0100_0001L));
    }
}
