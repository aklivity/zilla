/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.config.exporter.otlp.internal;

import static io.aklivity.zilla.config.engine.OptionsConfigAdapterSpi.Kind.EXPORTER;
import static java.util.stream.Collectors.toList;

import java.time.Duration;
import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.config.engine.OptionsConfig;
import io.aklivity.zilla.config.engine.OptionsConfigAdapterSpi;
import io.aklivity.zilla.config.exporter.otlp.OtlpOptionsConfig;
import io.aklivity.zilla.config.exporter.otlp.OtlpOptionsConfigBuilder;

public class OtlpOptionsConfigAdapter implements OptionsConfigAdapterSpi, JsonbAdapter<OptionsConfig, JsonObject>
{
    private static final String INTERVAL_NAME = "interval";
    private static final String SIGNALS_NAME = "signals";
    private static final String ENDPOINT_NAME = "endpoint";
    private static final String KEYS_NAME = "keys";
    private static final String TRUST_NAME = "trust";
    private static final String TRUSTCACERTS_NAME = "trustcacerts";
    private static final String AUTHORIZATION_NAME = "authorization";
    private static final String AUTHORIZATION_CREDENTIALS_NAME = "credentials";
    private static final String AUTHORIZATION_CREDENTIALS_HEADERS_NAME = "headers";
    private static final String TLS_NAME = "tls";

    private final OtlpSignalsAdapter signals;
    private final OtlpEndpointAdapter endpoint;

    public OtlpOptionsConfigAdapter()
    {
        this.signals = new OtlpSignalsAdapter();
        this.endpoint = new OtlpEndpointAdapter();
    }

    @Override
    public Kind kind()
    {
        return EXPORTER;
    }

    @Override
    public String type()
    {
        return "otlp";
    }

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        OtlpOptionsConfig otlpOptionsConfig = (OtlpOptionsConfig) options;
        JsonObjectBuilder object = Json.createObjectBuilder();
        if (otlpOptionsConfig.interval != null)
        {
            object.add(INTERVAL_NAME, otlpOptionsConfig.interval.toSeconds());
        }
        if (otlpOptionsConfig.signals != null)
        {
            object.add(SIGNALS_NAME, signals.adaptToJson(otlpOptionsConfig.signals));
        }
        if (otlpOptionsConfig.endpoint != null)
        {
            object.add(ENDPOINT_NAME, endpoint.adaptToJson(otlpOptionsConfig.endpoint));
        }
        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        OtlpOptionsConfigBuilder<OtlpOptionsConfig> builder = OtlpOptionsConfig.builder();

        if (object.containsKey(INTERVAL_NAME))
        {
            builder.interval(Duration.ofSeconds(object.getInt(INTERVAL_NAME)));
        }

        if (object.containsKey(SIGNALS_NAME))
        {
            builder.signals(signals.adaptFromJson(object.getJsonArray(SIGNALS_NAME)));
        }

        if (object.containsKey(ENDPOINT_NAME))
        {
            builder.endpoint(endpoint.adaptFromJson(object.getJsonObject(ENDPOINT_NAME)));
        }

        if (object.containsKey(TLS_NAME))
        {
            JsonObject tls = object.getJsonObject(TLS_NAME);

            if (tls.containsKey(KEYS_NAME))
            {
                builder.keys(asListString(tls.getJsonArray(KEYS_NAME)));
            }

            if (tls.containsKey(TRUST_NAME))
            {
                builder.trust(asListString(tls.getJsonArray(TRUST_NAME)));
            }

            if (tls.containsKey(TRUSTCACERTS_NAME))
            {
                builder.trustcacerts(tls.getBoolean(TRUSTCACERTS_NAME));
            }
        }

        if (object.containsKey(AUTHORIZATION_CREDENTIALS_NAME))
        {
            JsonObject credentials = object.getJsonObject(AUTHORIZATION_CREDENTIALS_NAME);

            JsonObject headers = credentials.getJsonObject(AUTHORIZATION_CREDENTIALS_HEADERS_NAME);

            builder.authorization(headers.getString(AUTHORIZATION_NAME));
        }

        return builder.build();
    }

    private static List<String> asListString(
        JsonArray array)
    {
        return array.stream()
            .map(OtlpOptionsConfigAdapter::asString)
            .collect(toList());
    }

    private static String asString(
        JsonValue value)
    {
        switch (value.getValueType())
        {
        case STRING:
            return ((JsonString) value).getString();
        case NULL:
            return null;
        default:
            throw new IllegalArgumentException("Unexpected type: " + value.getValueType());
        }
    }
}
