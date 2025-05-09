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
package io.aklivity.zilla.runtime.binding.grpc.kafka.internal.config;

import java.util.function.Consumer;
import java.util.function.Supplier;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.binding.grpc.kafka.internal.types.KafkaHeaderFW;
public final class GrpcKafkaWithProduceOverrideResult
{
    public final DirectBuffer name;
    public final Supplier<DirectBuffer> valueRef;

    private final Consumer<DirectBuffer> updateHash;

    GrpcKafkaWithProduceOverrideResult(
        DirectBuffer name,
        Supplier<DirectBuffer> valueRef,
        Consumer<DirectBuffer> updateHash)
    {
        this.name = name;
        this.valueRef = valueRef;
        this.updateHash = updateHash;
    }

    public void header(
        KafkaHeaderFW.Builder builder)
    {
        final DirectBuffer value = valueRef.get();
        builder.nameLen(name.capacity())
               .name(name, 0, name.capacity())
               .valueLen(value.capacity())
               .value(value, 0, value.capacity());

        updateHash.accept(name);
        updateHash.accept(value);
    }
}
