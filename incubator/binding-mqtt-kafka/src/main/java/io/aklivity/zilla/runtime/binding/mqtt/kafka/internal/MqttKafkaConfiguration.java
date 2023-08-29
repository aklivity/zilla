/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.mqtt.kafka.internal;

import static java.time.Instant.now;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import org.agrona.LangUtil;

import io.aklivity.zilla.runtime.engine.Configuration;

public class MqttKafkaConfiguration extends Configuration
{
    public static final String MQTT_CLIENTS_GROUP_ID = "mqtt-clients";
    private static final ConfigurationDef MQTT_KAFKA_CONFIG;

    public static final PropertyDef<String> MESSAGES_TOPIC;
    public static final PropertyDef<String> RETAINED_MESSAGES_TOPIC;
    public static final PropertyDef<String> SESSIONS_TOPIC;
    public static final PropertyDef<StringSupplier> SESSION_ID;
    public static final PropertyDef<StringSupplier> WILL_ID;
    public static final PropertyDef<StringSupplier> LIFETIME_ID;
    public static final PropertyDef<StringSupplier> INSTANCE_ID;
    public static final PropertyDef<LongSupplier> TIME;
    public static final BooleanPropertyDef WILL_AVAILABLE;
    public static final IntPropertyDef WILL_STREAM_RECONNECT_DELAY;
    public static final IntPropertyDef SESSION_EXPIRY_INTERVAL;

    static
    {
        final ConfigurationDef config = new ConfigurationDef("zilla.binding.mqtt.kafka");
        MESSAGES_TOPIC = config.property("messages.topic", "mqtt_messages");
        RETAINED_MESSAGES_TOPIC = config.property("retained.messages.topic", "mqtt_retained");
        SESSIONS_TOPIC = config.property("sessions.topic", "mqtt_sessions");
        SESSION_ID = config.property(StringSupplier.class, "session.id",
            MqttKafkaConfiguration::decodeStringSupplier, MqttKafkaConfiguration::defaultSessionId);
        WILL_ID = config.property(StringSupplier.class, "will.id",
            MqttKafkaConfiguration::decodeStringSupplier, MqttKafkaConfiguration::defaultWillId);
        LIFETIME_ID = config.property(StringSupplier.class, "lifetime.id",
            MqttKafkaConfiguration::decodeStringSupplier, MqttKafkaConfiguration::defaultLifetimeId);
        INSTANCE_ID = config.property(StringSupplier.class, "instance.id",
            MqttKafkaConfiguration::decodeStringSupplier, MqttKafkaConfiguration::defaultInstanceId);
        TIME = config.property(LongSupplier.class, "time",
            MqttKafkaConfiguration::decodeLongSupplier, MqttKafkaConfiguration::defaultTime);
        WILL_AVAILABLE = config.property("will.available", true);
        WILL_STREAM_RECONNECT_DELAY = config.property("will.stream.reconnect", 2);
        SESSION_EXPIRY_INTERVAL = config.property("session.expiry.interval", 30000);
        MQTT_KAFKA_CONFIG = config;
    }

    public MqttKafkaConfiguration(
        Configuration config)
    {
        super(MQTT_KAFKA_CONFIG, config);
    }

    @FunctionalInterface
    public interface StringSupplier extends Supplier<String>
    {
    }

    public Supplier<String> sessionId()
    {
        return SESSION_ID.get(this);
    }

    public Supplier<String> willId()
    {
        return WILL_ID.get(this);
    }

    public Supplier<String> lifetimeId()
    {
        return LIFETIME_ID.get(this);
    }

    public Supplier<String> instanceId()
    {
        return INSTANCE_ID.get(this);
    }

    public LongSupplier time()
    {
        return TIME.get(this);
    }

    public boolean willAvailable()
    {
        return WILL_AVAILABLE.get(this);
    }

    public int willStreamReconnectDelay()
    {
        return WILL_STREAM_RECONNECT_DELAY.getAsInt(this);
    }

    public int sessionExpiryInterval()
    {
        return SESSION_EXPIRY_INTERVAL.get(this);
    }

    private static StringSupplier decodeStringSupplier(
        String fullyQualifiedMethodName)
    {
        StringSupplier supplier = null;

        try
        {
            MethodType signature = MethodType.methodType(String.class);
            String[] parts = fullyQualifiedMethodName.split("::");
            Class<?> ownerClass = Class.forName(parts[0]);
            String methodName = parts[1];
            MethodHandle method = MethodHandles.publicLookup().findStatic(ownerClass, methodName, signature);
            supplier = () ->
            {
                String value = null;
                try
                {
                    value = (String) method.invoke();
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

    private static LongSupplier decodeLongSupplier(
        String fullyQualifiedMethodName)
    {
        LongSupplier supplier = null;

        try
        {
            MethodType signature = MethodType.methodType(long.class);
            String[] parts = fullyQualifiedMethodName.split("::");
            Class<?> ownerClass = Class.forName(parts[0]);
            String methodName = parts[1];
            MethodHandle method = MethodHandles.publicLookup().findStatic(ownerClass, methodName, signature);
            supplier = () ->
            {
                long value = 0;
                try
                {
                    value = (long) method.invoke();
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

    private static String defaultInstanceId()
    {
        return String.format("%s-%s", "zilla", UUID.randomUUID());
    }

    private static String defaultSessionId()
    {
        return String.format("%s-%s", "zilla", UUID.randomUUID());
    }

    private static String defaultWillId()
    {
        return String.format("%s", UUID.randomUUID());
    }

    private static String defaultLifetimeId()
    {
        return String.format("%s", UUID.randomUUID());
    }

    private static long defaultTime()
    {
        return now().toEpochMilli();
    }
}
