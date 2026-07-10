/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.mcp.kafka.internal;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.UUID;
import java.util.function.Supplier;

import org.agrona.LangUtil;

import io.aklivity.zilla.runtime.engine.Configuration;

public class McpKafkaConfiguration extends Configuration
{
    private static final ConfigurationDef MCP_KAFKA_CONFIG;

    public static final PropertyDef<SessionIdSupplier> MCP_KAFKA_SESSION_ID;
    public static final PropertyDef<String> MCP_KAFKA_CACHE_CLIENT_EXIT;

    static
    {
        final ConfigurationDef config = new ConfigurationDef("zilla.binding.mcp.kafka");
        MCP_KAFKA_SESSION_ID = config.property(SessionIdSupplier.class, "session.id",
            McpKafkaConfiguration::decodeSessionIdSupplier, McpKafkaConfiguration::defaultSessionIdSupplier);
        MCP_KAFKA_CACHE_CLIENT_EXIT = config.property("cache.client.exit", "");
        MCP_KAFKA_CONFIG = config;
    }

    public McpKafkaConfiguration()
    {
        super(MCP_KAFKA_CONFIG, new Configuration());
    }

    public McpKafkaConfiguration(
        Configuration config)
    {
        super(MCP_KAFKA_CONFIG, config);
    }

    public Supplier<String> sessionIdSupplier()
    {
        return MCP_KAFKA_SESSION_ID.get(this)::get;
    }

    public String cacheClientExit()
    {
        return MCP_KAFKA_CACHE_CLIENT_EXIT.get(this);
    }

    private static SessionIdSupplier decodeSessionIdSupplier(
        String value)
    {
        SessionIdSupplier supplier = null;

        try
        {
            final MethodType signature = MethodType.methodType(String.class);
            final String[] parts = value.split("::");
            final Class<?> ownerClass = Class.forName(parts[0]);
            final String methodName = parts[1];
            final MethodHandle method = MethodHandles.publicLookup().findStatic(ownerClass, methodName, signature);
            supplier = () ->
            {
                String sessionId = null;
                try
                {
                    sessionId = (String) method.invoke();
                }
                catch (Throwable ex)
                {
                    LangUtil.rethrowUnchecked(ex);
                }

                return sessionId;
            };
        }
        catch (Throwable ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }

        return supplier;
    }

    private static String defaultSessionIdSupplier()
    {
        return UUID.randomUUID().toString();
    }

    @FunctionalInterface
    public interface SessionIdSupplier
    {
        String get();
    }
}
