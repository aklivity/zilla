/*
 * Copyright 2021-2023 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.mqtt.internal;

import java.util.concurrent.TimeUnit;

import io.aklivity.zilla.runtime.engine.Configuration;

public class MqttConfiguration extends Configuration
{
    private static final ConfigurationDef MQTT_CONFIG;
    public static final LongPropertyDef CONNECT_TIMEOUT;
    public static final LongPropertyDef PUBLISH_TIMEOUT;
    public static final ShortPropertyDef KEEP_ALIVE_MINIMUM;
    public static final ShortPropertyDef KEEP_ALIVE_MAXIMUM;
    public static final BytePropertyDef MAXIMUM_QOS;
    public static final BooleanPropertyDef RETAIN_AVAILABLE;
    public static final ShortPropertyDef TOPIC_ALIAS_MAXIMUM;
    public static final BooleanPropertyDef WILDCARD_SUBSCRIPTION_AVAILABLE;
    public static final BooleanPropertyDef SUBSCRIPTION_IDENTIFIERS_AVAILABLE;
    public static final BooleanPropertyDef SHARED_SUBSCRIPTION_AVAILABLE;
    public static final BooleanPropertyDef SESSIONS_AVAILABLE;
    public static final BooleanPropertyDef NO_LOCAL;
    public static final IntPropertyDef SESSION_EXPIRY_GRACE_PERIOD;
    public static final PropertyDef<String> CLIENT_ID;
    public static final PropertyDef<String> SERVER_REFERENCE;

    static
    {
        final ConfigurationDef config = new ConfigurationDef("zilla.binding.mqtt");
        PUBLISH_TIMEOUT = config.property("publish.timeout", TimeUnit.SECONDS.toSeconds(30));
        CONNECT_TIMEOUT = config.property("connect.timeout", TimeUnit.SECONDS.toSeconds(3));
        //TODO: better default values?
        KEEP_ALIVE_MINIMUM = config.property("keep.alive.minimum", (short) 10);
        KEEP_ALIVE_MAXIMUM = config.property("keep.alive.maximum", (short) 1000);
        MAXIMUM_QOS = config.property("maximum.qos", (byte) 0);
        RETAIN_AVAILABLE = config.property("retain.available", true);
        TOPIC_ALIAS_MAXIMUM = config.property("topic.alias.maximum", (short) 0);
        WILDCARD_SUBSCRIPTION_AVAILABLE = config.property("wildcard.subscription.available", true);
        SUBSCRIPTION_IDENTIFIERS_AVAILABLE = config.property("subscription.identifiers.available", true);
        SHARED_SUBSCRIPTION_AVAILABLE = config.property("shared.subscription.available", false);
        SESSIONS_AVAILABLE = config.property("sessions.available", true);
        NO_LOCAL = config.property("no.local", true);
        SESSION_EXPIRY_GRACE_PERIOD = config.property("session.expiry.grace.period", 30);
        CLIENT_ID = config.property("client.id");
        SERVER_REFERENCE = config.property("server.reference");
        MQTT_CONFIG = config;
    }

    public MqttConfiguration(
        Configuration config)
    {
        super(MQTT_CONFIG, config);
    }

    public long publishTimeout()
    {
        return PUBLISH_TIMEOUT.get(this);
    }

    public long connectTimeout()
    {
        return CONNECT_TIMEOUT.get(this);
    }

    public boolean retainAvailable()
    {
        return RETAIN_AVAILABLE.get(this);
    }

    public short keepAliveMinimum()
    {
        return KEEP_ALIVE_MINIMUM.get(this);
    }

    public short keepAliveMaximum()
    {
        return KEEP_ALIVE_MAXIMUM.get(this);
    }

    public byte maximumQos()
    {
        return MAXIMUM_QOS.get(this);
    }

    public short topicAliasMaximum()
    {
        return TOPIC_ALIAS_MAXIMUM.get(this);
    }

    public boolean wildcardSubscriptionAvailable()
    {
        return WILDCARD_SUBSCRIPTION_AVAILABLE.get(this);
    }

    public boolean subscriptionIdentifierAvailable()
    {
        return SUBSCRIPTION_IDENTIFIERS_AVAILABLE.get(this);
    }

    public boolean sharedSubscriptionAvailable()
    {
        return SHARED_SUBSCRIPTION_AVAILABLE.get(this);
    }

    public boolean sessionsAvailable()
    {
        return SESSIONS_AVAILABLE.get(this);
    }

    public boolean noLocal()
    {
        return NO_LOCAL.get(this);
    }

    public int sessionExpiryGracePeriod()
    {
        return SESSION_EXPIRY_GRACE_PERIOD.get(this);
    }

    public String clientId()
    {
        return CLIENT_ID.get(this);
    }

    public String serverReference()
    {
        return SERVER_REFERENCE.get(this);
    }
}
