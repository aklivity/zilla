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
package io.aklivity.zilla.runtime.binding.sse.internal;

import io.aklivity.zilla.runtime.engine.Configuration;

public class SseConfiguration extends Configuration
{
    public static final String CHALLENGE_EVENT_TYPE_NAME = "zilla.binding.sse.challenge.event.type";

    public static final BooleanPropertyDef INITIAL_COMMENT_ENABLED;
    public static final IntPropertyDef MAXIMUM_IDLE_TIME;

    private static final ConfigurationDef SSE_CONFIG;

    static final PropertyDef<String> CHALLENGE_EVENT_TYPE;

    static
    {
        final ConfigurationDef config = new ConfigurationDef("zilla.binding.sse");
        INITIAL_COMMENT_ENABLED = config.property("initial.comment.enabled", false);
        CHALLENGE_EVENT_TYPE = config.property("challenge.event.type", "challenge");
        MAXIMUM_IDLE_TIME = config.property("maximum.idle.time", 0);
        SSE_CONFIG = config;
    }

    public SseConfiguration(
        Configuration config)
    {
        super(SSE_CONFIG, config);
    }

    public boolean initialCommentEnabled()
    {
        return INITIAL_COMMENT_ENABLED.getAsBoolean(this);
    }

    public String challengeEventType()
    {
        return CHALLENGE_EVENT_TYPE.get(this);
    }

    public int maximumIdleTime()
    {
        return MAXIMUM_IDLE_TIME.getAsInt(this);
    }
}
