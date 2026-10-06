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

import io.aklivity.zilla.runtime.engine.Configuration;

public class SseConfiguration extends Configuration
{
    public static final String CHALLENGE_EVENT_TYPE_NAME = "zilla.binding.sse.challenge.event.type";

    public static final BooleanPropertyDef INITIAL_COMMENT_ENABLED;
    public static final IntPropertyDef MAXIMUM_IDLE_TIME;

    private static final ConfigurationDef SSE_CONFIG;

    private static final BooleanPropertyDef HTTP_INITIAL_COMMENT_ENABLED;
    private static final IntPropertyDef HTTP_MAXIMUM_IDLE_TIME;
    private static final PropertyDef<String> HTTP_CHALLENGE_EVENT_TYPE;

    static final PropertyDef<String> CHALLENGE_EVENT_TYPE;

    static
    {
        final ConfigurationDef http = new ConfigurationDef("zilla.binding.http");
        HTTP_INITIAL_COMMENT_ENABLED = http.property("event.stream.initial.comment.enabled", false);
        HTTP_MAXIMUM_IDLE_TIME = http.property("event.stream.maximum.idle.time", 0);
        HTTP_CHALLENGE_EVENT_TYPE = http.property("event.stream.challenge.event.type", "challenge");

        final ConfigurationDef config = new ConfigurationDef("zilla.binding.sse");
        INITIAL_COMMENT_ENABLED = config.property("initial.comment.enabled", HTTP_INITIAL_COMMENT_ENABLED::getAsBoolean);
        CHALLENGE_EVENT_TYPE = config.property("challenge.event.type", HTTP_CHALLENGE_EVENT_TYPE::get);
        MAXIMUM_IDLE_TIME = config.property("maximum.idle.time", HTTP_MAXIMUM_IDLE_TIME::getAsInt);
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
