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

import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_PROMPTS_LIST;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_RESOURCES_LIST;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_RESOURCES_TEMPLATES_LIST;
import static io.aklivity.zilla.runtime.binding.mcp.internal.types.stream.McpBeginExFW.KIND_TOOLS_LIST;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntPredicate;
import java.util.function.LongFunction;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;

import org.agrona.LangUtil;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.EngineConfiguration;

public class McpConfiguration extends Configuration
{
    private static final ConfigurationDef MCP_CONFIG;

    public static final PropertyDef<LongFunction<String>> MCP_SESSION_ID;
    public static final PropertyDef<ToLongFunction<String>> MCP_SESSION_ID_AFFINITY;
    public static final PropertyDef<ElicitationIdSupplier> MCP_ELICITATION_ID;
    public static final PropertyDef<ElicitationIdSupplier> MCP_ELICIT_CORRELATION_ID;
    public static final PropertyDef<String> MCP_SERVER_NAME;
    public static final PropertyDef<String> MCP_SERVER_VERSION;
    public static final PropertyDef<String> MCP_CLIENT_NAME;
    public static final PropertyDef<String> MCP_CLIENT_VERSION;
    public static final PropertyDef<Duration> MCP_INACTIVITY_TIMEOUT;
    public static final IntPropertyDef MCP_KEEPALIVE_TOLERANCE;
    public static final PropertyDef<Duration> MCP_SSE_KEEPALIVE_INTERVAL;
    public static final BooleanPropertyDef MCP_ALT_SVC_ENABLED;
    public static final PropertyDef<Duration> MCP_ALT_SVC_MAX_AGE;
    public static final PropertyDef<IntPredicate> MCP_HYDRATE_FILTER;
    public static final PropertyDef<Duration> MCP_LEASE_TTL;
    public static final PropertyDef<Duration> MCP_LEASE_RETRY;

    static
    {
        final ConfigurationDef config = new ConfigurationDef("zilla.binding.mcp");
        @SuppressWarnings("unchecked")
        final Class<LongFunction<String>> sessionIdKind = (Class<LongFunction<String>>) (Class<?>) LongFunction.class;
        MCP_SESSION_ID = config.property(sessionIdKind, "session.id",
            McpConfiguration::decodeSessionIdSupplier, McpConfiguration::newSessionId);
        @SuppressWarnings("unchecked")
        final Class<ToLongFunction<String>> sessionIdAffinityKind =
            (Class<ToLongFunction<String>>) (Class<?>) ToLongFunction.class;
        MCP_SESSION_ID_AFFINITY = config.property(sessionIdAffinityKind, "session.id.affinity",
            McpConfiguration::decodeSessionIdAffinity, McpConfiguration::defaultSessionIdAffinity);
        MCP_ELICITATION_ID = config.property(ElicitationIdSupplier.class, "elicitation.id",
            McpConfiguration::decodeElicitationIdSupplier, McpConfiguration::defaultElicitationIdSupplier);
        MCP_ELICIT_CORRELATION_ID = config.property(ElicitationIdSupplier.class, "elicit.correlation.id",
            McpConfiguration::decodeElicitationIdSupplier, McpConfiguration::defaultElicitCorrelationIdSupplier);
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
        MCP_KEEPALIVE_TOLERANCE = config.property("keepalive.tolerance", 2);
        MCP_SSE_KEEPALIVE_INTERVAL = config.property(Duration.class, "sse.keepalive.interval",
            (c, v) -> Duration.parse(v), "PT15S");
        MCP_ALT_SVC_ENABLED = config.property("alt.svc.enabled", McpConfiguration::defaultAltSvcEnabled);
        MCP_ALT_SVC_MAX_AGE = config.property(Duration.class, "alt.svc.max.age",
            (c, v) -> Duration.parse(v), "PT24H");
        MCP_HYDRATE_FILTER = config.property(IntPredicate.class, "hydrate.filter",
            McpConfiguration::decodeHydrateFilter, McpConfiguration::defaultHydrateFilter);
        MCP_LEASE_TTL = config.property(Duration.class, "lease.ttl",
            (c, v) -> Duration.parse(v), "PT30S");
        MCP_LEASE_RETRY = config.property(Duration.class, "lease.retry",
            (c, v) -> Duration.parse(v), "PT0.1S");
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

    public LongFunction<String> sessionIdSupplier()
    {
        return MCP_SESSION_ID.get(this);
    }

    public ToLongFunction<String> sessionIdAffinity()
    {
        return MCP_SESSION_ID_AFFINITY.get(this);
    }

    public Supplier<String> elicitationIdSupplier()
    {
        return MCP_ELICITATION_ID.get(this)::get;
    }

    public Supplier<String> elicitCorrelationIdSupplier()
    {
        return MCP_ELICIT_CORRELATION_ID.get(this)::get;
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

    public Duration sseKeepaliveInterval()
    {
        return MCP_SSE_KEEPALIVE_INTERVAL.get(this);
    }

    public boolean altSvcEnabled()
    {
        return MCP_ALT_SVC_ENABLED.getAsBoolean(this);
    }

    public Duration altSvcMaxAge()
    {
        return MCP_ALT_SVC_MAX_AGE.get(this);
    }

    public IntPredicate hydrateFilter()
    {
        return MCP_HYDRATE_FILTER.get(this);
    }

    public Duration leaseTtl()
    {
        return MCP_LEASE_TTL.get(this);
    }

    public Duration leaseRetry()
    {
        return MCP_LEASE_RETRY.get(this);
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

    private static LongFunction<String> decodeSessionIdSupplier(
        String value)
    {
        LongFunction<String> supplier = null;

        try
        {
            MethodType signature = MethodType.methodType(String.class, long.class);
            String[] parts = value.split("::");
            Class<?> ownerClass = Class.forName(parts[0]);
            String methodName = parts[1];
            MethodHandle method = MethodHandles.publicLookup().findStatic(ownerClass, methodName, signature);
            supplier = affinity ->
            {
                String sessionId = null;
                try
                {
                    sessionId = (String) method.invoke(affinity);
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

    private static String newSessionId(
        long affinity)
    {
        // affinity (top byte node id, low 3 bytes worker index, matching EngineWorker.affinity()'s
        // packing) is embedded verbatim into the low 32 bits of the UUID's least-significant bits
        final long leastSigBits = (SESSION_ID_RANDOM.nextLong() & 0xffffffff_00000000L) | (affinity & 0xffff_ffffL);
        final long mostSigBits = SESSION_ID_RANDOM.nextLong();
        return new UUID(mostSigBits, leastSigBits).toString();
    }

    private static ToLongFunction<String> decodeSessionIdAffinity(
        String value)
    {
        ToLongFunction<String> extractor = null;

        try
        {
            MethodType signature = MethodType.methodType(long.class, String.class);
            String[] parts = value.split("::");
            Class<?> ownerClass = Class.forName(parts[0]);
            String methodName = parts[1];
            MethodHandle method = MethodHandles.publicLookup().findStatic(ownerClass, methodName, signature);
            extractor = sessionId ->
            {
                long affinity = 0L;
                try
                {
                    affinity = (long) method.invoke(sessionId);
                }
                catch (Throwable ex)
                {
                    LangUtil.rethrowUnchecked(ex);
                }

                return affinity;
            };
        }
        catch (Throwable ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }

        return extractor;
    }

    private static long defaultSessionIdAffinity(
        String sessionId)
    {
        // newSessionId embeds affinity into the last 8 hex chars (offset 28) of the 36-char UUID
        // string it mints; parsed digit-by-digit here to avoid the allocation of a substring
        assert sessionId.length() == 36 : "session id must be a UUID-length value";
        long affinity = 0L;
        for (int i = 28; i < 36; i++)
        {
            affinity = (affinity << 4) | Character.digit(sessionId.charAt(i), 16);
        }
        return affinity;
    }

    private static boolean defaultAltSvcEnabled(
        Configuration config)
    {
        final String hostname = EngineConfiguration.ENGINE_SERVICE_HOSTNAME.get(config);
        return hostname != null && !hostname.isEmpty();
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

    private static IntPredicate decodeHydrateFilter(
        String value)
    {
        final Set<Integer> kinds = new HashSet<>();
        for (String name : value.split("\\s+"))
        {
            switch (name)
            {
            case "tools":
                kinds.add(KIND_TOOLS_LIST);
                break;
            case "resources":
                kinds.add(KIND_RESOURCES_LIST);
                kinds.add(KIND_RESOURCES_TEMPLATES_LIST);
                break;
            case "prompts":
                kinds.add(KIND_PROMPTS_LIST);
                break;
            default:
                break;
            }
        }
        return kinds::contains;
    }

    private static boolean defaultHydrateFilter(
        int kind)
    {
        return true;
    }

    private static String defaultElicitationIdSupplier()
    {
        final byte[] bytes = new byte[4];
        ELICITATION_ID_RANDOM.nextBytes(bytes);
        return ELICITATION_ID_HEX.formatHex(bytes);
    }

    private static String defaultElicitCorrelationIdSupplier()
    {
        final byte[] bytes = new byte[4];
        ELICITATION_ID_RANDOM.nextBytes(bytes);
        return ELICITATION_ID_HEX.formatHex(bytes);
    }

    private static final SecureRandom ELICITATION_ID_RANDOM = new SecureRandom();
    private static final HexFormat ELICITATION_ID_HEX = HexFormat.of();
    private static final SecureRandom SESSION_ID_RANDOM = new SecureRandom();
}
