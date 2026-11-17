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
package io.aklivity.zilla.runtime.engine.internal.registry;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import org.junit.Test;

public class EngineManagerTest
{
    @Test
    public void shouldLimitAffinityToLowestBits()
    {
        assertThat(EngineManager.limitAffinity(-1L, 1), equalTo(1L << 63));
        assertThat(EngineManager.limitAffinity(0xffff_ffffL, 1), equalTo(1L << 31));
        assertThat(EngineManager.limitAffinity(0xffff_ffffL, 31), equalTo(0xffff_fffeL));
        assertThat(EngineManager.limitAffinity(0xffff_ffff_ffffL, 16), equalTo(0xffff_0000_0000L));
        assertThat(EngineManager.limitAffinity(0xffffL, Integer.MAX_VALUE), equalTo(0xffffL));
    }
}
