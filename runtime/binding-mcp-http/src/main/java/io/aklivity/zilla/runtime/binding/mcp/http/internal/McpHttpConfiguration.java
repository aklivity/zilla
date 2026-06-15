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
package io.aklivity.zilla.runtime.binding.mcp.http.internal;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_WORKERS;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.UUID;
import java.util.function.Supplier;

import org.agrona.LangUtil;

import io.aklivity.zilla.runtime.engine.Configuration;

public class McpHttpConfiguration extends Configuration
{
    private static final ConfigurationDef MCP_HTTP_CONFIG;

    public static final PropertyDef<SessionIdSupplier> MCP_HTTP_SESSION_ID;
    public static final IntPropertyDef MCP_HTTP_SESSION_ID_ATTEMPTS;

    static
    {
        final ConfigurationDef config = new ConfigurationDef("zilla.binding.mcp.http");
        MCP_HTTP_SESSION_ID = config.property(SessionIdSupplier.class, "session.id",
            McpHttpConfiguration::decodeSessionIdSupplier, McpHttpConfiguration::defaultSessionIdSupplier);
        MCP_HTTP_SESSION_ID_ATTEMPTS = config.property("session.id.attempts",
            McpHttpConfiguration::defaultSessionIdAttempts);
        MCP_HTTP_CONFIG = config;
    }

    public McpHttpConfiguration(
        Configuration config)
    {
        super(MCP_HTTP_CONFIG, config);
    }

    public McpHttpConfiguration()
    {
        super(MCP_HTTP_CONFIG, new Configuration());
    }

    public Supplier<String> sessionIdSupplier()
    {
        return MCP_HTTP_SESSION_ID.get(this)::get;
    }

    public int sessionIdAttempts()
    {
        return MCP_HTTP_SESSION_ID_ATTEMPTS.getAsInt(this);
    }

    @FunctionalInterface
    public interface SessionIdSupplier
    {
        String get();
    }

    private static SessionIdSupplier decodeSessionIdSupplier(
        String value)
    {
        SessionIdSupplier supplier = null;

        try
        {
            MethodType signature = MethodType.methodType(String.class);
            String[] parts = value.split("::");
            Class<?> ownerClass = Class.forName(parts[0]);
            String methodName = parts[1];
            MethodHandle method = MethodHandles.publicLookup().findStatic(ownerClass, methodName, signature);
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

    private static int defaultSessionIdAttempts(
        Configuration config)
    {
        return Math.max(1, ENGINE_WORKERS.getAsInt(config) * 64);
    }
}
