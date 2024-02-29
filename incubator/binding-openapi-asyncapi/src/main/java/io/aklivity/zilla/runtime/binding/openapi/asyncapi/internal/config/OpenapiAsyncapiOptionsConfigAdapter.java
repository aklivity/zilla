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
package io.aklivity.zilla.runtime.binding.openapi.asyncapi.internal.config;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.unmodifiableSet;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.zip.CRC32C;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiParser;
import io.aklivity.zilla.runtime.binding.openapi.asyncapi.config.AsyncapiConfig;
import io.aklivity.zilla.runtime.binding.openapi.asyncapi.config.OpenapiAsyncapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.openapi.asyncapi.config.OpenapiAsyncapiSpecConfig;
import io.aklivity.zilla.runtime.binding.openapi.asyncapi.config.OpenapiConfig;
import io.aklivity.zilla.runtime.binding.openapi.asyncapi.internal.OpenapiAsyncapiBinding;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiParser;
import io.aklivity.zilla.runtime.engine.config.ConfigAdapterContext;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;

public final class OpenapiAsyncapiOptionsConfigAdapter implements OptionsConfigAdapterSpi, JsonbAdapter<OptionsConfig, JsonObject>
{
    private static final String OPENAPI_NAME = "openapi";
    private static final String ASYNCAPI_NAME = "asyncapi";
    private static final String SPECS_NAME = "specs";

    private final CRC32C crc;

    private final OpenapiParser openapiParser = new OpenapiParser();
    private final AsyncapiParser asyncapiParser = new AsyncapiParser();

    private Function<String, String> readURL;

    public OpenapiAsyncapiOptionsConfigAdapter()
    {
        crc = new CRC32C();
    }

    @Override
    public Kind kind()
    {
        return Kind.BINDING;
    }

    @Override
    public String type()
    {
        return OpenapiAsyncapiBinding.NAME;
    }

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        OpenapiAsyncapiOptionsConfig proxyOptions = (OpenapiAsyncapiOptionsConfig) options;

        JsonObjectBuilder object = Json.createObjectBuilder();
        JsonObjectBuilder spec = Json.createObjectBuilder();

        JsonObjectBuilder openapi = Json.createObjectBuilder();
        proxyOptions.specs.openapi.forEach(o -> openapi.add(o.apiLabel, o.location));
        spec.add(OPENAPI_NAME, openapi);

        JsonObjectBuilder asyncapi = Json.createObjectBuilder();
        proxyOptions.specs.asyncapi.forEach(a -> asyncapi.add(a.apiLabel, a.location));
        spec.add(ASYNCAPI_NAME, asyncapi);

        object.add(SPECS_NAME, spec);

        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        JsonObject specs = object.getJsonObject(SPECS_NAME);

        JsonObject openapi = specs.getJsonObject(OPENAPI_NAME);
        Set<OpenapiConfig> openapis = new LinkedHashSet<>();
        openapi.forEach((n, v) ->
        {
            final String location = JsonString.class.cast(v).getString();
            final String specText = readURL.apply(location);
            final String apiLabel = n;
            crc.reset();
            crc.update(specText.getBytes(UTF_8));
            final long apiId = crc.getValue();
            openapis.add(new OpenapiConfig(apiLabel, apiId, location, openapiParser.parse(specText)));
        });

        JsonObject asyncapi = specs.getJsonObject(ASYNCAPI_NAME);
        Set<AsyncapiConfig> asyncapis = new LinkedHashSet<>();
        asyncapi.forEach((n, v) ->
        {
            final String location = JsonString.class.cast(v).getString();
            final String specText = readURL.apply(location);
            final String apiLabel = n;
            crc.reset();
            crc.update(specText.getBytes(UTF_8));
            final long apiId = crc.getValue();
            asyncapis.add(new AsyncapiConfig(apiLabel, apiId, location, asyncapiParser.parse(specText)));
        });

        OpenapiAsyncapiSpecConfig specConfig = new OpenapiAsyncapiSpecConfig(
            unmodifiableSet(openapis), unmodifiableSet(asyncapis));

        return new OpenapiAsyncapiOptionsConfig(specConfig);
    }

    @Override
    public void adaptContext(
        ConfigAdapterContext context)
    {
        this.readURL = context::readURL;
    }
}
