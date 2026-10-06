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
package io.aklivity.zilla.runtime.binding.sse.internal;

import static io.aklivity.zilla.runtime.binding.sse.internal.SseConfiguration.CHALLENGE_EVENT_TYPE;
import static io.aklivity.zilla.runtime.binding.sse.internal.SseConfiguration.CHALLENGE_EVENT_TYPE_NAME;
import static io.aklivity.zilla.runtime.binding.sse.internal.SseConfiguration.INITIAL_COMMENT_ENABLED;
import static io.aklivity.zilla.runtime.binding.sse.internal.SseConfiguration.MAXIMUM_IDLE_TIME;
import static org.junit.Assert.assertEquals;

import java.util.Properties;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.Configuration;

public class SseConfigurationTest
{
    // needed by test annotations
    public static final String SSE_INITIAL_COMMENT_ENABLED_NAME = "zilla.binding.sse.initial.comment.enabled";
    public static final String SSE_MAXIMUM_IDLE_TIME_NAME = "zilla.binding.sse.maximum.idle.time";

    @Test
    public void shouldVerifyConstants() throws Exception
    {
        assertEquals(CHALLENGE_EVENT_TYPE.name(), CHALLENGE_EVENT_TYPE_NAME);
        assertEquals(INITIAL_COMMENT_ENABLED.name(), SSE_INITIAL_COMMENT_ENABLED_NAME);
        assertEquals(MAXIMUM_IDLE_TIME.name(), SSE_MAXIMUM_IDLE_TIME_NAME);
    }

    @Test
    public void shouldDefaultToHttpEventStreamProperties() throws Exception
    {
        final Properties properties = new Properties();
        properties.setProperty("zilla.binding.http.sse.initial.comment.enabled", "true");
        properties.setProperty("zilla.binding.http.sse.maximum.idle.time", "7");

        final SseConfiguration config = new SseConfiguration(new Configuration(properties));

        assertEquals(true, config.initialCommentEnabled());
        assertEquals(7, config.maximumIdleTime());
    }

    @Test
    public void shouldOverrideHttpEventStreamPropertiesWithSseProperties() throws Exception
    {
        final Properties properties = new Properties();
        properties.setProperty("zilla.binding.http.sse.maximum.idle.time", "7");
        properties.setProperty(SSE_MAXIMUM_IDLE_TIME_NAME, "3");

        final SseConfiguration config = new SseConfiguration(new Configuration(properties));

        assertEquals(3, config.maximumIdleTime());
    }
}
