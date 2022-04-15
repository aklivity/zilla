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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

import org.junit.Before;
import org.junit.Test;

import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.String16FW;
import io.aklivity.zilla.runtime.binding.http.kafka.internal.types.String8FW;

public class HttpKafkaOptionsConfigAdapterTest
{
    private Jsonb jsonb;

    @Before
    public void initJson()
    {
        JsonbConfig config = new JsonbConfig()
                .withAdapters(new HttpKafkaOptionsConfigAdapter());
        jsonb = JsonbBuilder.create(config);
    }

    @Test
    public void shouldReadOptions()
    {
        String text =
                "{" +
                    "\"idempotency\":" +
                    "{" +
                        "\"header\":\"idempotency-key\"" +
                    "}," +
                    "\"correlation\":" +
                    "{" +
                        "\"headers\":" +
                        "{" +
                            "\"reply-to\":\"zilla:reply-to\"," +
                            "\"correlation-id\":\"zilla:correlation-id\"" +
                        "}" +
                    "}" +
                "}";

        HttpKafkaOptionsConfig options = jsonb.fromJson(text, HttpKafkaOptionsConfig.class);

        assertThat(options, not(nullValue()));
        assertThat(options.idempotency, not(nullValue()));
        assertThat(options.idempotency.header.asString(), equalTo("idempotency-key"));
        assertThat(options.correlation, not(nullValue()));
        assertThat(options.correlation.replyTo.asString(), equalTo("zilla:reply-to"));
        assertThat(options.correlation.correlationId.asString(), equalTo("zilla:correlation-id"));
    }

    @Test
    public void shouldWriteOptions()
    {
        HttpKafkaOptionsConfig options = new HttpKafkaOptionsConfig(
                new HttpKafkaIdempotencyConfig(
                    new String8FW("x-idempotency-key")),
                new HttpKafkaCorrelationConfig(
                    new String16FW("zilla:x-reply-to"),
                    new String16FW("zilla:x-correlation-id")));

        String text = jsonb.toJson(options);

        assertThat(text, not(nullValue()));
        assertThat(text, equalTo(
                "{" +
                    "\"idempotency\":" +
                    "{" +
                        "\"header\":\"x-idempotency-key\"" +
                    "}," +
                    "\"correlation\":" +
                    "{" +
                        "\"headers\":" +
                        "{" +
                            "\"reply-to\":\"zilla:x-reply-to\"," +
                            "\"correlation-id\":\"zilla:x-correlation-id\"" +
                        "}" +
                    "}" +
                "}"));
    }
}
