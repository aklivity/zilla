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
package io.aklivity.zilla.runtime.binding.openapi.internal.config;

import static io.aklivity.zilla.runtime.binding.http.internal.config.HttpOptionsConfigAdapter.adaptAuthorization;
import static io.aklivity.zilla.runtime.binding.http.internal.config.HttpOptionsConfigAdapter.adaptAuthorizationFromObject;
import static org.agrona.LangUtil.rethrowUnchecked;

import java.util.function.Function;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.http.config.HttpAuthorizationConfig;
import io.aklivity.zilla.runtime.binding.http.config.HttpAuthorizationConfigBuilder;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiConfig;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenpaiOptionsConfigBuilder;
import io.aklivity.zilla.runtime.binding.openapi.internal.OpenapiBinding;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.OpenApi;
import io.aklivity.zilla.runtime.binding.tls.config.TlsOptionsConfig;
import io.aklivity.zilla.runtime.binding.tls.internal.config.TlsOptionsConfigAdapter;
import io.aklivity.zilla.runtime.engine.config.ConfigAdapterContext;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;

public final class OpenapiOptionsConfigAdapter implements OptionsConfigAdapterSpi, JsonbAdapter<OptionsConfig, JsonObject>
{
    private static final String TLS_NAME = "tls";
    private static final String HTTP_NAME = "http";
    private static final String AUTHORIZATION_NAME = "authorization";
    private static final String SPECS_NAME = "specs";
    private final TlsOptionsConfigAdapter tlsOptionsConfigAdapter = new TlsOptionsConfigAdapter();
    private Function<String, String> readURL;

    @Override
    public Kind kind()
    {
        return Kind.BINDING;
    }

    @Override
    public String type()
    {
        return OpenapiBinding.NAME;
    }

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        OpenapiOptionsConfig openOptions = (OpenapiOptionsConfig) options;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (openOptions.tls != null)
        {
            object.add(SPECS_NAME, tlsOptionsConfigAdapter
                .adaptToJson(((OpenapiOptionsConfig) options).tls));
        }

        HttpAuthorizationConfig httpAuthorization = openOptions.authorization;
        if (httpAuthorization != null)
        {
            JsonObjectBuilder http = Json.createObjectBuilder();
            JsonObjectBuilder authorization = Json.createObjectBuilder();

            adaptAuthorizationFromObject(httpAuthorization, authorization);

            http.add(AUTHORIZATION_NAME, authorization.build());
            object.add(HTTP_NAME, http.build());
        }

        if (openOptions.openapis != null)
        {
            JsonArrayBuilder keys = Json.createArrayBuilder();
            openOptions.openapis.forEach(p -> keys.add(p.location));
            object.add(SPECS_NAME, keys);
        }

        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        OpenpaiOptionsConfigBuilder<OpenapiOptionsConfig> openapiOptions = OpenapiOptionsConfig.builder();

        if (object.containsKey(TLS_NAME))
        {
            openapiOptions.tls((TlsOptionsConfig) tlsOptionsConfigAdapter
                .adaptFromJson(object.getJsonObject(TLS_NAME)));
        }

        if (object.containsKey(HTTP_NAME))
        {
            HttpAuthorizationConfigBuilder<?> httpAuthorization = openapiOptions.authorization();
            JsonObject http = object.getJsonObject(HTTP_NAME);
            JsonObject authorizations = http.getJsonObject(AUTHORIZATION_NAME);

            adaptAuthorization(authorizations, httpAuthorization);

            httpAuthorization.build();
        }

        if (object.containsKey(SPECS_NAME))
        {
            object.getJsonArray(SPECS_NAME).forEach(s -> openapiOptions.openapi(asOpenapi(s)));
        }

        return openapiOptions.build();
    }

    @Override
    public void adaptContext(
        ConfigAdapterContext context)
    {
        this.readURL = context::readURL;
    }

    private OpenapiConfig asOpenapi(
        JsonValue value)
    {
        final String location = ((JsonString) value).getString();
        final String specText = readURL.apply(location);
        OpenApi openapi = parseOpenApi(specText);

        return new OpenapiConfig(location, openapi);
    }

    private OpenApi parseOpenApi(
        String openapiText)
    {
        OpenApi openApi = null;
        try (Jsonb jsonb = JsonbBuilder.create())
        {
            openApi = jsonb.fromJson(openapiText, OpenApi.class);
        }
        catch (Exception ex)
        {
            rethrowUnchecked(ex);
        }
        return openApi;
    }
}
