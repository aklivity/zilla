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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;

import org.agrona.LangUtil;

import io.aklivity.zilla.runtime.binding.mqtt.internal.types.MqttQoS;
import io.aklivity.zilla.runtime.engine.Configuration;

public class MqttConfiguration extends Configuration
{
    private static final ConfigurationDef MQTT_CONFIG;
    public static final LongPropertyDef CONNECT_TIMEOUT;
    public static final LongPropertyDef CONNACK_TIMEOUT;
    public static final LongPropertyDef PUBLISH_TIMEOUT;
    public static final ShortPropertyDef KEEP_ALIVE_MINIMUM;
    public static final ShortPropertyDef KEEP_ALIVE_MAXIMUM;
    public static final BytePropertyDef MAXIMUM_QOS;
    public static final BooleanPropertyDef RETAIN_AVAILABLE;
    public static final ShortPropertyDef TOPIC_ALIAS_MAXIMUM;
    public static final BooleanPropertyDef WILDCARD_SUBSCRIPTION;
    public static final BooleanPropertyDef SUBSCRIPTION_IDENTIFIERS;
    public static final BooleanPropertyDef SHARED_SUBSCRIPTION;
    public static final BooleanPropertyDef NO_LOCAL;
    public static final IntPropertyDef SESSION_EXPIRY_GRACE_PERIOD;
    public static final PropertyDef<String> CLIENT_ID;
    public static final PropertyDef<IntSupplier> SUBSCRIPTION_ID;
    public static final PropertyDef<MqttQoS> PUBLISH_QOS_MAX;
    public static final int GENERATED_SUBSCRIPTION_ID_MASK = 0x70;

    static
    {
        final ConfigurationDef config = new ConfigurationDef("zilla.binding.mqtt");
        PUBLISH_TIMEOUT = config.property("publish.timeout", TimeUnit.MINUTES.toSeconds(2));
        CONNECT_TIMEOUT = config.property("connect.timeout", TimeUnit.SECONDS.toSeconds(3));
        CONNACK_TIMEOUT = config.property("connack.timeout", TimeUnit.SECONDS.toSeconds(3));
        //TODO: better default values?
        KEEP_ALIVE_MINIMUM = config.property("keep.alive.minimum", (short) 10);
        KEEP_ALIVE_MAXIMUM = config.property("keep.alive.maximum", (short) 1000);
        MAXIMUM_QOS = config.property("maximum.qos", (byte) 0);
        RETAIN_AVAILABLE = config.property("retain.available", true);
        TOPIC_ALIAS_MAXIMUM = config.property("topic.alias.maximum", (short) 0);
        WILDCARD_SUBSCRIPTION = config.property("wildcard.subscription.available", true);
        SUBSCRIPTION_IDENTIFIERS = config.property("subscription.identifiers.available", true);
        SHARED_SUBSCRIPTION = config.property("shared.subscription.available", false);
        NO_LOCAL = config.property("no.local", true);
        SESSION_EXPIRY_GRACE_PERIOD = config.property("session.expiry.grace.period", 30);
        CLIENT_ID = config.property("client.id");
        SUBSCRIPTION_ID = config.property(IntSupplier.class, "subscription.id",
            MqttConfiguration::decodeIntSupplier, MqttConfiguration::defaultSubscriptionId);
        PUBLISH_QOS_MAX = config.property(MqttQoS.class, "publish.qos.max",
            MqttConfiguration::decodePublishQosMax, MqttQoS.EXACTLY_ONCE);
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

    public long connackTimeout()
    {
        return CONNACK_TIMEOUT.get(this);
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

    public IntSupplier subscriptionId()
    {
        return SUBSCRIPTION_ID.get(this);
    }

    public MqttQoS publishQosMax()
    {
        return PUBLISH_QOS_MAX.get(this);
    }

    private static MqttQoS decodePublishQosMax(
        String value)
    {
        return MqttQoS.valueOf(value.toUpperCase());
    }

    private static IntSupplier decodeIntSupplier(
        String fullyQualifiedMethodName)
    {
        IntSupplier supplier = null;

        try
        {
            MethodType signature = MethodType.methodType(int.class);
            String[] parts = fullyQualifiedMethodName.split("::");
            Class<?> ownerClass = Class.forName(parts[0]);
            String methodName = parts[1];
            MethodHandle method = MethodHandles.publicLookup().findStatic(ownerClass, methodName, signature);
            supplier = () ->
            {
                int value = 0;
                try
                {
                    value = (int) method.invoke();
                }
                catch (Throwable ex)
                {
                    LangUtil.rethrowUnchecked(ex);
                }

                return value;
            };
        }
        catch (Throwable ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }

        return supplier;
    }

    private static int defaultSubscriptionId()
    {
        int randomValue = Math.abs(new Random().nextInt());
        return randomValue | GENERATED_SUBSCRIPTION_ID_MASK;
    }
}
