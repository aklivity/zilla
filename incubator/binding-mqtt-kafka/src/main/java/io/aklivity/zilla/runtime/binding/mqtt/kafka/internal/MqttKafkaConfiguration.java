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
import java.util.function.Supplier;

import org.agrona.LangUtil;

import io.aklivity.zilla.runtime.engine.Configuration;

public class MqttKafkaConfiguration extends Configuration
{
    private static final ConfigurationDef MQTT_KAFKA_CONFIG;

    public static final PropertyDef<String> MESSAGES_TOPIC;
    public static final PropertyDef<String> RETAINED_MESSAGES_TOPIC;
    public static final PropertyDef<String> SESSIONS_TOPIC;
    public static final PropertyDef<String> SESSION_ID;
    public static final PropertyDef<String> WILL_ID;
    public static final PropertyDef<String> LIFETIME_ID;
    public static final PropertyDef<String> INSTANCE_ID;
    public static final PropertyDef<Long> TIME;
    public static final BooleanPropertyDef WILL_AVAILABLE;

    static
    {
        final ConfigurationDef config = new ConfigurationDef("zilla.binding.mqtt.kafka");
        MESSAGES_TOPIC = config.property("messages.topic", "mqtt_messages");
        RETAINED_MESSAGES_TOPIC = config.property("retained.messages.topic", "mqtt_retained");
        SESSIONS_TOPIC = config.property("sessions.topic", "mqtt_sessions");
        SESSION_ID = config.property(String.class, "session.id",
            MqttKafkaConfiguration::decodeStringSupplier, MqttKafkaConfiguration::defaultSessionIdSupplier);
        WILL_ID = config.property(String.class, "will.id",
            MqttKafkaConfiguration::decodeStringSupplier, MqttKafkaConfiguration::defaultWillIdSupplier);
        LIFETIME_ID = config.property(String.class, "lifetime.id",
            MqttKafkaConfiguration::decodeStringSupplier, MqttKafkaConfiguration::defaultLifetimeIdSupplier);
        INSTANCE_ID = config.property(String.class, "instance.id",
            MqttKafkaConfiguration::decodeStringSupplier, MqttKafkaConfiguration::defaultInstanceIdSupplier);
        TIME = config.property(Long.class, "time",
            MqttKafkaConfiguration::decodeLongSupplier, MqttKafkaConfiguration::defaultTimeSupplier);
        WILL_AVAILABLE = config.property("will.available", true);
        MQTT_KAFKA_CONFIG = config;
    }

    public MqttKafkaConfiguration(
        Configuration config)
    {
        super(MQTT_KAFKA_CONFIG, config);
    }

    public Supplier<String> sessionIdSupplier()
    {
        return () -> SESSION_ID.get(this);
    }

    public Supplier<String> willIdSupplier()
    {
        return () -> WILL_ID.get(this);
    }

    public Supplier<String> lifetimeIdSupplier()
    {
        return () -> LIFETIME_ID.get(this);
    }

    public Supplier<String> instanceIdSupplier()
    {
        return () -> INSTANCE_ID.get(this);
    }

    public Supplier<Long> timeSupplier()
    {
        return () -> TIME.get(this);
    }

    public boolean willAvailable()
    {
        return WILL_AVAILABLE.get(this);
    }


    private static String decodeStringSupplier(
        Configuration config,
        String fullyQualifiedMethodName)
    {
        Supplier<String> supplier = null;

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

        return supplier.get();
    }

    private static Long decodeLongSupplier(
        Configuration config,
        String fullyQualifiedMethodName)
    {
        Supplier<Long> supplier = null;

        try
        {
            MethodType signature = MethodType.methodType(Long.class);
            String[] parts = fullyQualifiedMethodName.split("::");
            Class<?> ownerClass = Class.forName(parts[0]);
            String methodName = parts[1];
            MethodHandle method = MethodHandles.publicLookup().findStatic(ownerClass, methodName, signature);
            supplier = () ->
            {
                Long value = null;
                try
                {
                    value = (Long) method.invoke();
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

        return supplier.get();
    }

    private static String defaultInstanceIdSupplier(
        Configuration config)
    {
        return String.format("%s-%s", "zilla", UUID.randomUUID());
    }

    private static String defaultSessionIdSupplier(
        Configuration config)
    {
        return String.format("%s-%s", "zilla", UUID.randomUUID());
    }

    private static String defaultWillIdSupplier(
        Configuration config)
    {
        return String.format("%s", UUID.randomUUID());
    }

    private static String defaultLifetimeIdSupplier(
        Configuration config)
    {
        return String.format("%s", UUID.randomUUID());
    }

    private static Long defaultTimeSupplier(
        Configuration config)
    {
        return now().toEpochMilli();
    }
}
