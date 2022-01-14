/*
 * Copyright 2021-2022 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.sse.internal;

import static io.aklivity.zilla.runtime.binding.sse.internal.SseConfiguration.CHALLENGE_EVENT_TYPE;
import static io.aklivity.zilla.runtime.binding.sse.internal.SseConfiguration.CHALLENGE_EVENT_TYPE_NAME;
import static io.aklivity.zilla.runtime.binding.sse.internal.SseConfiguration.INITIAL_COMMENT_ENABLED;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SseConfigurationTest
{
    // needed by test annotations
    public static final String SSE_INITIAL_COMMENT_ENABLED_NAME = "zilla.binding.sse.initial.comment.enabled";

    @Test
    public void shouldVerifyConstants() throws Exception
    {
        assertEquals(INITIAL_COMMENT_ENABLED.name(), SSE_INITIAL_COMMENT_ENABLED_NAME);
    }

    @Test
    public void shouldMatchChallengeEventTypeConfigName()
    {
        assertEquals(CHALLENGE_EVENT_TYPE_NAME, CHALLENGE_EVENT_TYPE.name());
    }
}
