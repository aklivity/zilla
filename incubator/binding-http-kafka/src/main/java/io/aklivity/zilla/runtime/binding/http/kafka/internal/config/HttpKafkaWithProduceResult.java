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

import java.util.List;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.Array32FW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.HttpHeaderFW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.KafkaHeaderFW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.String16FW;

public class HttpKafkaWithProduceResult
{
    private final String16FW topic;
    private final DirectBuffer key;
    private final List<HttpKafkaWithProduceOverrideResult> overrides;
    private final String16FW replyTo;
    private final List<HttpKafkaWithProduceAsyncHeaderResult> async;

    HttpKafkaWithProduceResult(
        String16FW topic,
        DirectBuffer key,
        List<HttpKafkaWithProduceOverrideResult> overrides,
        String16FW replyTo,
        List<HttpKafkaWithProduceAsyncHeaderResult> async)
    {
        this.topic = topic;
        this.key = key;
        this.overrides = overrides;
        this.replyTo = replyTo;
        this.async = async;
    }

    public String16FW topic()
    {
        return topic;
    }

    public DirectBuffer key()
    {
        return key;
    }

    public void overrides(
        Array32FW.Builder<KafkaHeaderFW.Builder, KafkaHeaderFW> builder)
    {
        if (overrides != null)
        {
            overrides.forEach(o -> builder.item(o::header));
        }
    }

    public String16FW replyTo()
    {
        return replyTo;
    }

    public void async(
        Array32FW.Builder<HttpHeaderFW.Builder, HttpHeaderFW> builder)
    {
        if (async != null)
        {
            async.forEach(a -> builder.item(a::header));
        }
    }
}
