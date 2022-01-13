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
package io.aklivity.zilla.runtime.cog.amqp.internal.config;

import static io.aklivity.zilla.runtime.cog.amqp.internal.AmqpConfiguration.AMQP_CHANNEL_MAX;
import static io.aklivity.zilla.runtime.cog.amqp.internal.AmqpConfiguration.AMQP_CLOSE_EXCHANGE_TIMEOUT;
import static io.aklivity.zilla.runtime.cog.amqp.internal.AmqpConfiguration.AMQP_CONTAINER_ID;
import static io.aklivity.zilla.runtime.cog.amqp.internal.AmqpConfiguration.AMQP_HANDLE_MAX;
import static io.aklivity.zilla.runtime.cog.amqp.internal.AmqpConfiguration.AMQP_IDLE_TIMEOUT;
import static io.aklivity.zilla.runtime.cog.amqp.internal.AmqpConfiguration.AMQP_INCOMING_LOCALES;
import static io.aklivity.zilla.runtime.cog.amqp.internal.AmqpConfiguration.AMQP_INITIAL_DEVIVERY_COUNT;
import static io.aklivity.zilla.runtime.cog.amqp.internal.AmqpConfiguration.AMQP_MAX_FRAME_SIZE;
import static io.aklivity.zilla.runtime.cog.amqp.internal.AmqpConfiguration.AMQP_MAX_MESSAGE_SIZE;
import static io.aklivity.zilla.runtime.cog.amqp.internal.AmqpConfiguration.AMQP_OUTGOING_WINDOW;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AmqpConfigurationTest
{
    public static final String AMQP_CONTAINER_ID_NAME = "zilla.binding.amqp.container.id";
    public static final String AMQP_CHANNEL_MAX_NAME = "zilla.binding.amqp.channel.max";
    public static final String AMQP_MAX_FRAME_SIZE_NAME = "zilla.binding.amqp.max.frame.size";
    public static final String AMQP_HANDLE_MAX_NAME = "zilla.binding.amqp.handle.max";
    public static final String AMQP_MAX_MESSAGE_SIZE_NAME = "zilla.binding.amqp.max.message.size";
    public static final String AMQP_IDLE_TIMEOUT_NAME = "zilla.binding.amqp.idle.timeout";
    public static final String AMQP_OUTGOING_WINDOW_NAME = "zilla.binding.amqp.outgoing.window";
    public static final String AMQP_INITIAL_DEVIVERY_COUNT_NAME = "zilla.binding.amqp.initial.delivery.count";
    public static final String AMQP_CLOSE_EXCHANGE_TIMEOUT_NAME = "zilla.binding.amqp.close.exchange.timeout";
    public static final String AMQP_INCOMING_LOCALES_NAME = "zilla.binding.amqp.incoming.locales";

    @Test
    public void shouldVerifyConstants() throws Exception
    {
        assertEquals(AMQP_CONTAINER_ID.name(), AMQP_CONTAINER_ID_NAME);
        assertEquals(AMQP_CHANNEL_MAX.name(), AMQP_CHANNEL_MAX_NAME);
        assertEquals(AMQP_MAX_FRAME_SIZE.name(), AMQP_MAX_FRAME_SIZE_NAME);
        assertEquals(AMQP_HANDLE_MAX.name(), AMQP_HANDLE_MAX_NAME);
        assertEquals(AMQP_MAX_MESSAGE_SIZE.name(), AMQP_MAX_MESSAGE_SIZE_NAME);
        assertEquals(AMQP_IDLE_TIMEOUT.name(), AMQP_IDLE_TIMEOUT_NAME);
        assertEquals(AMQP_OUTGOING_WINDOW.name(), AMQP_OUTGOING_WINDOW_NAME);
        assertEquals(AMQP_INITIAL_DEVIVERY_COUNT.name(), AMQP_INITIAL_DEVIVERY_COUNT_NAME);
        assertEquals(AMQP_CLOSE_EXCHANGE_TIMEOUT.name(), AMQP_CLOSE_EXCHANGE_TIMEOUT_NAME);
        assertEquals(AMQP_INCOMING_LOCALES.name(), AMQP_INCOMING_LOCALES_NAME);
    }
}
