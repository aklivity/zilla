/*
 * Copyright 2021-2022 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.sse.kafka.internal;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import io.aklivity.zilla.runtime.engine.Configuration;

public class SseKafkaConfiguration extends Configuration
{
    public static final String CHALLENGE_EVENT_TYPE_NAME = "zilla.binding.sse.challenge.event.type";

    public static final BooleanPropertyDef INITIAL_COMMENT_ENABLED;

    private static final DirectBuffer INITIAL_COMMENT_DEFAULT = new UnsafeBuffer(new byte[0]);

    private static final ConfigurationDef SSE_CONFIG;

    static final PropertyDef<String> CHALLENGE_EVENT_TYPE;

    static
    {
        final ConfigurationDef config = new ConfigurationDef("zilla.binding.sse");
        INITIAL_COMMENT_ENABLED = config.property("initial.comment.enabled", false);
        CHALLENGE_EVENT_TYPE = config.property("challenge.event.type", "challenge");
        SSE_CONFIG = config;
    }

    public SseKafkaConfiguration(
        Configuration config)
    {
        super(SSE_CONFIG, config);
    }

    public DirectBuffer initialComment()
    {
        return INITIAL_COMMENT_ENABLED.getAsBoolean(this) ? INITIAL_COMMENT_DEFAULT : null;
    }

    public String getChallengeEventType()
    {
        return CHALLENGE_EVENT_TYPE.get(this);
    }

}
