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
package io.aklivity.zilla.runtime.binding.a2a.http.internal;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.UUID;
import java.util.function.LongFunction;

import org.agrona.LangUtil;

import io.aklivity.zilla.runtime.engine.Configuration;

public class A2aHttpConfiguration extends Configuration
{
    private static final ConfigurationDef A2A_HTTP_CONFIG;

    public static final PropertyDef<LongFunction<String>> A2A_HTTP_TASK_ID;
    public static final LongPropertyDef A2A_HTTP_LOCK_TTL_SECONDS;
    public static final LongPropertyDef A2A_HTTP_TIMEOUT_SECONDS;

    static
    {
        final ConfigurationDef config = new ConfigurationDef("zilla.binding.a2a.http");
        @SuppressWarnings("unchecked")
        final Class<LongFunction<String>> taskIdKind = (Class<LongFunction<String>>) (Class<?>) LongFunction.class;
        A2A_HTTP_TASK_ID = config.property(taskIdKind, "task.id",
            A2aHttpConfiguration::decodeTaskIdSupplier, A2aHttpConfiguration::newTaskId);
        A2A_HTTP_LOCK_TTL_SECONDS = config.property("lock.ttl.seconds", 30L);
        A2A_HTTP_TIMEOUT_SECONDS = config.property("timeout.seconds", 30L);
        A2A_HTTP_CONFIG = config;
    }

    public A2aHttpConfiguration(
        Configuration config)
    {
        super(A2A_HTTP_CONFIG, config);
    }

    public A2aHttpConfiguration()
    {
        super(A2A_HTTP_CONFIG, new Configuration());
    }

    public LongFunction<String> taskIdSupplier()
    {
        return A2A_HTTP_TASK_ID.get(this);
    }

    public Duration lockTtl()
    {
        return Duration.ofSeconds(A2A_HTTP_LOCK_TTL_SECONDS.getAsLong(this));
    }

    public Duration timeout()
    {
        return Duration.ofSeconds(A2A_HTTP_TIMEOUT_SECONDS.getAsLong(this));
    }

    private static LongFunction<String> decodeTaskIdSupplier(
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
                String taskId = null;
                try
                {
                    taskId = (String) method.invoke(affinity);
                }
                catch (Throwable ex)
                {
                    LangUtil.rethrowUnchecked(ex);
                }

                return taskId;
            };
        }
        catch (Throwable ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }

        return supplier;
    }

    private static String newTaskId(
        long affinity)
    {
        final long leastSigBits = (TASK_ID_RANDOM.nextLong() & 0xffffffff_00000000L) | (affinity & 0xffff_ffffL);
        final long mostSigBits = TASK_ID_RANDOM.nextLong();
        return new UUID(mostSigBits, leastSigBits).toString();
    }

    private static final SecureRandom TASK_ID_RANDOM = new SecureRandom();
}
