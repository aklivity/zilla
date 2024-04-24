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
package io.aklivity.zilla.runtime.binding.asyncapi.internal;

import io.aklivity.zilla.runtime.engine.Configuration;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;

import org.agrona.LangUtil;

public class AsyncapiConfiguration extends Configuration
{
    private static final AtomicInteger COMPOSITE_NAMESPACE_COUNTER = new AtomicInteger(0);
    public static final LongPropertyDef ASYNCAPI_TARGET_ROUTE_ID;
    public static final PropertyDef<IntSupplier> COMPOSITE_NAMESPACE_POSTFIX;
    private static final ConfigurationDef ASYNCAPI_CONFIG;

    static
    {
        final ConfigurationDef config = new ConfigurationDef("zilla.binding.asyncapi");
        ASYNCAPI_TARGET_ROUTE_ID = config.property("target.route.id", -1L);
        COMPOSITE_NAMESPACE_POSTFIX = config.property(IntSupplier.class, "composite.namespace.postfix",
            AsyncapiConfiguration::decodeIntSupplier, AsyncapiConfiguration::defaultCompositeNamespacePostfix);
        ASYNCAPI_CONFIG = config;
    }

    public AsyncapiConfiguration(
        Configuration config)
    {
        super(ASYNCAPI_CONFIG, config);
    }

    public long targetRouteId()
    {
        return ASYNCAPI_TARGET_ROUTE_ID.getAsLong(this);
    }

    private static IntSupplier decodeIntSupplier(
        String fullyQualifiedMethodName)
    {
        IntSupplier supplier = null;

        try
        {
            MethodType signature = MethodType.methodType(int.class);
            String[] parts = fullyQualifiedMethodName.split("::");
            Class<?> ownerClass = Class.forName(parts[0]);
            String methodName = parts[1];
            MethodHandle method = MethodHandles.publicLookup().findStatic(ownerClass, methodName, signature);
            supplier = () ->
            {
                int value = 0;
                try
                {
                    value = (int) method.invoke();
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

    private static int defaultCompositeNamespacePostfix()
    {
        return COMPOSITE_NAMESPACE_COUNTER.getAndIncrement();
    }
}
