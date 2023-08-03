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

import java.lang.reflect.Method;
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
    public static final PropertyDef<SessionIdSupplier> SESSION_ID_SUPPLIER;

    static
    {
        final ConfigurationDef config = new ConfigurationDef("zilla.binding.mqtt.kafka");
        MESSAGES_TOPIC = config.property("messages.topic", "mqtt_messages");
        RETAINED_MESSAGES_TOPIC = config.property("retained.messages.topic", "mqtt_retained");
        SESSIONS_TOPIC = config.property("sessions.topic", "mqtt_sessions");
        SESSION_ID_SUPPLIER = config.property(SessionIdSupplier.class, "session.id.supplier",
            MqttKafkaConfiguration::decodeSessionIdSupplier, MqttKafkaConfiguration::defaultSessionIdSupplier);
        MQTT_KAFKA_CONFIG = config;
    }

    public MqttKafkaConfiguration(
        Configuration config)
    {
        super(MQTT_KAFKA_CONFIG, config);
    }

    public Supplier<String> sessionIdSupplier()
    {
        return SESSION_ID_SUPPLIER.get(this);
    }

    @FunctionalInterface
    public interface SessionIdSupplier extends Supplier<String>
    {
    }

    private static SessionIdSupplier decodeSessionIdSupplier(
        Configuration config,
        String value)
    {
        try
        {
            String className = value.substring(0, value.indexOf("$$Lambda"));
            Class<?> lambdaClass = Class.forName(className);

            Method targetMethod = null;
            for (Method method : lambdaClass.getDeclaredMethods())
            {
                if (method.isSynthetic())
                {
                    targetMethod = method;
                    break;
                }
            }

            Method finalTargetMethod = targetMethod;
            return () ->
            {
                try
                {
                    finalTargetMethod.setAccessible(true);
                    return (String) finalTargetMethod.invoke(null);
                }
                catch (Exception e)
                {
                    throw new RuntimeException("Failed to invoke the lambda method.", e);
                }
            };
        }
        catch (Throwable ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }
        return null;
    }

    private static SessionIdSupplier defaultSessionIdSupplier(
        Configuration config)
    {
        return () -> String.format("%s-%s", "zilla", UUID.randomUUID());
    }
}
