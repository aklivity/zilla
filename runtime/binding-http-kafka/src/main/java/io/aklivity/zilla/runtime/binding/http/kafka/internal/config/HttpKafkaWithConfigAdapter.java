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

import jakarta.json.JsonObject;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.http.kafka.internal.HttpKafkaBinding;
import io.aklivity.zilla.runtime.engine.config.WithConfig;
import io.aklivity.zilla.runtime.engine.config.WithConfigAdapterSpi;

public final class HttpKafkaWithConfigAdapter implements WithConfigAdapterSpi, JsonbAdapter<WithConfig, JsonObject>
{
    private static final String CAPABILITY_NAME = "capability";

    private final HttpKafkaWithFetchConfigAdapter fetch = new HttpKafkaWithFetchConfigAdapter();
    private final HttpKafkaWithProduceConfigAdapter produce = new HttpKafkaWithProduceConfigAdapter();

    @Override
    public String type()
    {
        return HttpKafkaBinding.NAME;
    }

    @Override
    public JsonObject adaptToJson(
        WithConfig with)
    {
        JsonObject newWith = null;

        HttpKafkaWithConfig httpKafkaWith = (HttpKafkaWithConfig) with;
        switch (httpKafkaWith.capability)
        {
        case FETCH:
            newWith = fetch.adaptToJson(httpKafkaWith);
            break;
        case PRODUCE:
            newWith = produce.adaptToJson(httpKafkaWith);
            break;
        }

        return newWith;
    }

    @Override
    public WithConfig adaptFromJson(
        JsonObject object)
    {
        HttpKafkaWithConfig newWith = null;

        HttpKafkaCapability newCapability = HttpKafkaCapability.of(object.getString(CAPABILITY_NAME));
        switch (newCapability)
        {
        case FETCH:
            newWith = fetch.adaptFromJson(object);
            break;
        case PRODUCE:
            newWith = produce.adaptFromJson(object);
            break;
        }

        return newWith;
    }
}
