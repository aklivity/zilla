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

import java.util.Objects;

import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.String16FW;

public final class HttpKafkaCorrelationConfig
{
    public final String16FW replyTo;
    public final String16FW correlationId;

    public HttpKafkaCorrelationConfig(
        String replyTo,
        String correlationId)
    {
        this(new String16FW(replyTo), new String16FW(correlationId));
    }

    public HttpKafkaCorrelationConfig(
        String16FW replyTo,
        String16FW correlationId)
    {
        this.replyTo = replyTo;
        this.correlationId = correlationId;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(replyTo, correlationId);
    }

    @Override
    public boolean equals(
        Object obj)
    {
        boolean equals = obj instanceof HttpKafkaCorrelationConfig;

        if (equals)
        {
            HttpKafkaCorrelationConfig that = (HttpKafkaCorrelationConfig) obj;

            equals = Objects.equals(this.replyTo, that.replyTo) &&
                    Objects.equals(this.correlationId, that.correlationId);
        }

        return equals;
    }
}
