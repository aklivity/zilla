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
package io.aklivity.zilla.runtime.binding.mcp.http.internal;


import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.security.SecureRandom;
import java.util.UUID;
import java.util.function.LongFunction;

import org.agrona.LangUtil;

import io.aklivity.zilla.runtime.engine.Configuration;

public class McpHttpConfiguration extends Configuration
{
    private static final ConfigurationDef MCP_HTTP_CONFIG;

    public static final PropertyDef<LongFunction<String>> MCP_HTTP_SESSION_ID;
    public static final PropertyDef<String> MCP_HTTP_CLIENT_EXIT;

    static
    {
        final ConfigurationDef config = new ConfigurationDef("zilla.binding.mcp.http");
        @SuppressWarnings("unchecked")
        final Class<LongFunction<String>> sessionIdKind = (Class<LongFunction<String>>) (Class<?>) LongFunction.class;
        MCP_HTTP_SESSION_ID = config.property(sessionIdKind, "session.id",
            McpHttpConfiguration::decodeSessionIdSupplier, McpHttpConfiguration::newSessionId);
        MCP_HTTP_CLIENT_EXIT = config.property("client.exit", "sys:http_client");
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

    public LongFunction<String> sessionIdSupplier()
    {
        return MCP_HTTP_SESSION_ID.get(this);
    }

    public String clientExit()
    {
        return MCP_HTTP_CLIENT_EXIT.get(this);
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

    private static final SecureRandom SESSION_ID_RANDOM = new SecureRandom();
}
