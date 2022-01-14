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
package io.aklivity.zilla.runtime.binding.amqp.internal;

import io.aklivity.zilla.runtime.engine.Configuration;

public class AmqpConfiguration extends Configuration
{
    public static final PropertyDef<String> AMQP_CONTAINER_ID;
    public static final IntPropertyDef AMQP_CHANNEL_MAX;
    public static final LongPropertyDef AMQP_MAX_FRAME_SIZE;
    public static final LongPropertyDef AMQP_HANDLE_MAX;
    public static final LongPropertyDef AMQP_MAX_MESSAGE_SIZE;
    public static final LongPropertyDef AMQP_IDLE_TIMEOUT;
    public static final IntPropertyDef AMQP_OUTGOING_WINDOW;
    public static final LongPropertyDef AMQP_INITIAL_DEVIVERY_COUNT;
    public static final IntPropertyDef AMQP_CLOSE_EXCHANGE_TIMEOUT;
    public static final PropertyDef<String[]> AMQP_INCOMING_LOCALES;
    private static final ConfigurationDef AMQP_CONFIG;

    public static final String[] AMQP_INCOMING_LOCALES_DEFAULT = { "en-US" };

    static
    {
        final ConfigurationDef config = new ConfigurationDef("zilla.binding.amqp");
        AMQP_CONTAINER_ID = config.property("container.id", "zilla");
        AMQP_CHANNEL_MAX = config.property("channel.max", 65535);
        AMQP_MAX_FRAME_SIZE = config.property("max.frame.size", 4294967295L);
        AMQP_HANDLE_MAX = config.property("handle.max", 4294967295L);
        AMQP_MAX_MESSAGE_SIZE = config.property("max.message.size", 0L);
        AMQP_IDLE_TIMEOUT = config.property("idle.timeout", 0L);
        AMQP_OUTGOING_WINDOW = config.property("outgoing.window", Integer.MAX_VALUE);
        AMQP_INITIAL_DEVIVERY_COUNT = config.property("initial.delivery.count", 0L);
        AMQP_CLOSE_EXCHANGE_TIMEOUT = config.property("close.exchange.timeout", 10000);
        AMQP_INCOMING_LOCALES = config.property(String[].class, "incoming.locales",
            s -> s.split("\\s+"), c -> AMQP_INCOMING_LOCALES_DEFAULT);
        AMQP_CONFIG = config;
    }

    public AmqpConfiguration(
        Configuration config)
    {
        super(AMQP_CONFIG, config);
    }

    public String containerId()
    {
        return AMQP_CONTAINER_ID.get(this);
    }

    public int channelMax()
    {
        return AMQP_CHANNEL_MAX.getAsInt(this);
    }

    public long maxFrameSize()
    {
        return AMQP_MAX_FRAME_SIZE.getAsLong(this);
    }

    public long handleMax()
    {
        return AMQP_HANDLE_MAX.getAsLong(this);
    }

    public long maxMessageSize()
    {
        return AMQP_MAX_MESSAGE_SIZE.getAsLong(this);
    }

    public long idleTimeout()
    {
        return AMQP_IDLE_TIMEOUT.getAsLong(this);
    }

    public int outgoingWindow()
    {
        return AMQP_OUTGOING_WINDOW.getAsInt(this);
    }

    public long initialDeliveryCount()
    {
        return AMQP_INITIAL_DEVIVERY_COUNT.getAsLong(this);
    }

    public int closeExchangeTimeout()
    {
        return AMQP_CLOSE_EXCHANGE_TIMEOUT.getAsInt(this);
    }

    public String[] incomingLocales()
    {
        return AMQP_INCOMING_LOCALES.get(this);
    }
}
