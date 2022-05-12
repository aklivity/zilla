/*
 * Copyright 2021-2022 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.http.kafka.internal.config;

import java.util.function.Supplier;

import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.String8FW;

public final class HttpKafkaWithProduceAsyncHeaderResult
{
    public final String8FW name;
    public final Supplier<String16FW> valueRef;

    HttpKafkaWithProduceAsyncHeaderResult(
        String8FW name,
        String16FW value)
    {
        this(name, () -> value);
    }

    HttpKafkaWithProduceAsyncHeaderResult(
        String8FW name,
        Supplier<String16FW> valueRef)
    {
        this.name = name;
        this.valueRef = valueRef;
    }

    public void header(
        HttpHeaderFW.Builder builder)
    {
        final String16FW value = valueRef.get();

        builder.name(name)
               .value(value);
    }
}
