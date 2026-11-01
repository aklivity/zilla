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
package io.aklivity.zilla.runtime.binding.agui.a2a.internal;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.function.Supplier;

import org.agrona.LangUtil;

import io.aklivity.zilla.runtime.engine.Configuration;

public class AguiA2aConfiguration extends Configuration
{
    private static final ConfigurationDef AGUI_A2A_CONFIG;

    public static final PropertyDef<Duration> AGUI_A2A_THREAD_TTL;
    public static final IntPropertyDef AGUI_A2A_THREAD_CAPACITY;
    public static final PropertyDef<Duration> AGUI_A2A_HANDLE_TTL;
    public static final PropertyDef<Supplier<String>> AGUI_A2A_HANDLE_ID;

    static
    {
        final ConfigurationDef config = new ConfigurationDef("zilla.binding.agui.a2a");
        AGUI_A2A_THREAD_TTL = config.property(Duration.class, "thread.ttl",
            (c, v) -> Duration.parse(v), "PT1H");
        AGUI_A2A_THREAD_CAPACITY = config.property("thread.capacity", 4096);
        AGUI_A2A_HANDLE_TTL = config.property(Duration.class, "handle.ttl",
            (c, v) -> Duration.parse(v), "PT15M");
        @SuppressWarnings("unchecked")
        final Class<Supplier<String>> handleIdKind = (Class<Supplier<String>>) (Class<?>) Supplier.class;
        AGUI_A2A_HANDLE_ID = config.property(handleIdKind, "handle.id",
            AguiA2aConfiguration::decodeHandleIdSupplier, AguiA2aConfiguration::newHandleId);
        AGUI_A2A_CONFIG = config;
    }

    public AguiA2aConfiguration(
        Configuration config)
    {
        super(AGUI_A2A_CONFIG, config);
    }

    public AguiA2aConfiguration()
    {
        super(AGUI_A2A_CONFIG, new Configuration());
    }

    public Duration threadTtl()
    {
        return AGUI_A2A_THREAD_TTL.get(this);
    }

    public int threadCapacity()
    {
        return AGUI_A2A_THREAD_CAPACITY.getAsInt(this);
    }

    public Duration handleTtl()
    {
        return AGUI_A2A_HANDLE_TTL.get(this);
    }

    public Supplier<String> handleIdSupplier()
    {
        return AGUI_A2A_HANDLE_ID.get(this);
    }

    private static Supplier<String> decodeHandleIdSupplier(
        String value)
    {
        Supplier<String> supplier = null;

        try
        {
            MethodType signature = MethodType.methodType(String.class);
            String[] parts = value.split("::");
            Class<?> ownerClass = Class.forName(parts[0]);
            String methodName = parts[1];
            MethodHandle method = MethodHandles.publicLookup().findStatic(ownerClass, methodName, signature);
            supplier = () ->
            {
                String handleId = null;
                try
                {
                    handleId = (String) method.invoke();
                }
                catch (Throwable ex)
                {
                    LangUtil.rethrowUnchecked(ex);
                }

                return handleId;
            };
        }
        catch (Throwable ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }

        return supplier;
    }

    private static String newHandleId()
    {
        return String.format("%016x%016x", HANDLE_ID_RANDOM.nextLong(), HANDLE_ID_RANDOM.nextLong());
    }

    private static final SecureRandom HANDLE_ID_RANDOM = new SecureRandom();
}
