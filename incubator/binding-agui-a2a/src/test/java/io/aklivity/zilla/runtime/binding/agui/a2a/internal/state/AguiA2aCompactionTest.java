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
package io.aklivity.zilla.runtime.binding.agui.a2a.internal.state;

import static io.aklivity.zilla.runtime.binding.agui.a2a.internal.state.AguiA2aCompaction.MESSAGES_SNAPSHOT_FIRST;
import static io.aklivity.zilla.runtime.binding.agui.a2a.internal.state.AguiA2aCompaction.NONE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import org.junit.Test;

public class AguiA2aCompactionTest
{
    @Test
    public void shouldResetWhenPriorTurnNothingRememberedAndFreshContext()
    {
        assertThat(AguiA2aCompaction.decide(true, null, "ctx-2"), equalTo(MESSAGES_SNAPSHOT_FIRST));
    }

    @Test
    public void shouldNotResetOnFirstTurn()
    {
        assertThat(AguiA2aCompaction.decide(false, null, "ctx-1"), equalTo(NONE));
    }

    @Test
    public void shouldNotResetWhenContextRemembered()
    {
        assertThat(AguiA2aCompaction.decide(true, "ctx-1", "ctx-1"), equalTo(NONE));
    }

    @Test
    public void shouldNotResetForStatelessAgent()
    {
        assertThat(AguiA2aCompaction.decide(true, null, null), equalTo(NONE));
    }
}
