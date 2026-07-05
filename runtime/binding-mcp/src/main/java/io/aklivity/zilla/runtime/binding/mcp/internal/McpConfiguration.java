/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.mcp.internal;

import static io.aklivity.zilla.runtime.engine.EngineConfiguration.ENGINE_WORKERS;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;
import java.util.function.Supplier;

import org.agrona.LangUtil;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineConfiguration;

public class McpConfiguration extends Configuration
{
    private static final ConfigurationDef MCP_CONFIG;

    public static final PropertyDef<SessionIdSupplier> MCP_SESSION_ID;
    public static final PropertyDef<ElicitationIdSupplier> MCP_ELICITATION_ID;
    public static final PropertyDef<String> MCP_SERVER_NAME;
    public static final PropertyDef<String> MCP_SERVER_VERSION;
    public static final PropertyDef<String> MCP_CLIENT_NAME;
    public static final PropertyDef<String> MCP_CLIENT_VERSION;
    public static final PropertyDef<Duration> MCP_INACTIVITY_TIMEOUT;
    public static final IntPropertyDef MCP_SESSION_ID_ATTEMPTS;
    public static final IntPropertyDef MCP_KEEPALIVE_TOLERANCE;
    public static final PropertyDef<Duration> MCP_SSE_KEEPALIVE_INTERVAL;

    static
    {
        final ConfigurationDef config = new ConfigurationDef("zilla.binding.mcp");
        MCP_SESSION_ID = config.property(SessionIdSupplier.class, "session.id",
            McpConfiguration::decodeSessionIdSupplier, McpConfiguration::defaultSessionIdSupplier);
        MCP_ELICITATION_ID = config.property(ElicitationIdSupplier.class, "elicitation.id",
            McpConfiguration::decodeElicitationIdSupplier, McpConfiguration::defaultElicitationIdSupplier);
        MCP_SERVER_NAME = config.property(String.class, "server.name", (c, v) -> v,
            McpConfiguration::defaultServerName);
        MCP_SERVER_VERSION = config.property(String.class, "server.version", (c, v) -> v,
            McpConfiguration::defaultServerVersion);
        MCP_CLIENT_NAME = config.property(String.class, "client.name", (c, v) -> v,
            McpConfiguration::defaultServerName);
        MCP_CLIENT_VERSION = config.property(String.class, "client.version", (c, v) -> v,
            McpConfiguration::defaultServerVersion);
        MCP_INACTIVITY_TIMEOUT = config.property(Duration.class, "inactivity.timeout",
            (c, v) -> Duration.parse(v), "PT60S");
        MCP_SESSION_ID_ATTEMPTS = config.property("session.id.attempts",
            McpConfiguration::defaultSessionIdAttempts);
        MCP_KEEPALIVE_TOLERANCE = config.property("keepalive.tolerance", 2);
        MCP_SSE_KEEPALIVE_INTERVAL = config.property(Duration.class, "sse.keepalive.interval",
            (c, v) -> Duration.parse(v), "PT15S");
        MCP_CONFIG = config;
    }

    public McpConfiguration()
    {
        super(MCP_CONFIG, new Configuration());
    }

    public McpConfiguration(
        Configuration config)
    {
        super(MCP_CONFIG, config);
    }

    public Supplier<String> sessionIdSupplier()
    {
        return MCP_SESSION_ID.get(this)::get;
    }

    public Supplier<String> elicitationIdSupplier()
    {
        return MCP_ELICITATION_ID.get(this)::get;
    }

    public String serverName()
    {
        return MCP_SERVER_NAME.get(this);
    }

    public String serverVersion()
    {
        return MCP_SERVER_VERSION.get(this);
    }

    public String clientName()
    {
        return MCP_CLIENT_NAME.get(this);
    }

    public String clientVersion()
    {
        return MCP_CLIENT_VERSION.get(this);
    }

    public Duration inactivityTimeout()
    {
        return MCP_INACTIVITY_TIMEOUT.get(this);
    }

    public int keepaliveTolerance()
    {
        return MCP_KEEPALIVE_TOLERANCE.getAsInt(this);
    }

    public int sessionIdAttempts()
    {
        return MCP_SESSION_ID_ATTEMPTS.getAsInt(this);
    }

    public Duration sseKeepaliveInterval()
    {
        return MCP_SSE_KEEPALIVE_INTERVAL.get(this);
    }

    @FunctionalInterface
    public interface SessionIdSupplier
    {
        String get();
    }

    @FunctionalInterface
    public interface ElicitationIdSupplier
    {
        String get();
    }

    private static String defaultServerName(
        Configuration config)
    {
        return EngineConfiguration.ENGINE_NAME.get(config);
    }

    private static String defaultServerVersion(
        Configuration config)
    {
        return EngineConfiguration.ENGINE_VERSION.get(config);
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
        return Math.max(1, ENGINE_WORKERS.getAsInt(config) * 2);
    }

    private static ElicitationIdSupplier decodeElicitationIdSupplier(
        String value)
    {
        ElicitationIdSupplier supplier = null;

        try
        {
            MethodType signature = MethodType.methodType(String.class);
            String[] parts = value.split("::");
            Class<?> ownerClass = Class.forName(parts[0]);
            String methodName = parts[1];
            MethodHandle method = MethodHandles.publicLookup().findStatic(ownerClass, methodName, signature);
            supplier = () ->
            {
                String elicitationId = null;
                try
                {
                    elicitationId = (String) method.invoke();
                }
                catch (Throwable ex)
                {
                    LangUtil.rethrowUnchecked(ex);
                }

                return elicitationId;
            };
        }
        catch (Throwable ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }

        return supplier;
    }

    private static String defaultElicitationIdSupplier()
    {
        final byte[] bytes = new byte[4];
        ELICITATION_ID_RANDOM.nextBytes(bytes);
        return ELICITATION_ID_HEX.formatHex(bytes);
    }

    private static final SecureRandom ELICITATION_ID_RANDOM = new SecureRandom();
    private static final HexFormat ELICITATION_ID_HEX = HexFormat.of();
}
