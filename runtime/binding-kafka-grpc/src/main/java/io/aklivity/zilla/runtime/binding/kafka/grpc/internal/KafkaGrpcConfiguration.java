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
package io.aklivity.zilla.runtime.binding.kafka.grpc.internal;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.Supplier;

import org.agrona.LangUtil;

import io.aklivity.zilla.runtime.engine.Configuration;

public class KafkaGrpcConfiguration extends Configuration
{
    private static final ConfigurationDef KAFKA_GRPC_CONFIG;
    public static final PropertyDef<GroupIdSupplier> KAFKA_GROUP_ID;

    static
    {
        final ConfigurationDef config = new ConfigurationDef("zilla.binding.kafka.grpc");
        KAFKA_GROUP_ID = config.property(GroupIdSupplier.class, "group.id",
            KafkaGrpcConfiguration::decodeGroupId, KafkaGrpcConfiguration::defaultGroupId);
        KAFKA_GRPC_CONFIG = config;
    }

    public KafkaGrpcConfiguration(
        Configuration config)
    {
        super(KAFKA_GRPC_CONFIG, config);
    }

    public Supplier<String> groupIdSupplier()
    {
        return KAFKA_GROUP_ID.get(this)::get;
    }

    @FunctionalInterface
    private interface GroupIdSupplier extends Supplier<String>
    {
    }

    private static GroupIdSupplier decodeGroupId(
        Configuration config,
        String fullyQualifiedMethodName)
    {
        GroupIdSupplier supplier = null;

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

        return supplier;
    }

    private static GroupIdSupplier defaultGroupId(
        Configuration config)
    {
        return () -> "zilla:%s-%s";
    }
}
