/*
 * Copyright 2021-2023 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.binding.tls.internal.config;

import static io.aklivity.zilla.runtime.binding.tls.config.TlsMutualConfig.REQUIRED;
import static java.util.stream.Collectors.toList;

import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.tls.config.TlsMutualConfig;
import io.aklivity.zilla.runtime.binding.tls.config.TlsOptionsConfig;
import io.aklivity.zilla.runtime.binding.tls.config.TlsOptionsConfigBuilder;
import io.aklivity.zilla.runtime.binding.tls.internal.TlsBinding;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;

public final class TlsOptionsConfigAdapter implements OptionsConfigAdapterSpi, JsonbAdapter<OptionsConfig, JsonObject>
{
    private static final String VERSION_NAME = "version";
    private static final String KEYS_NAME = "keys";
    private static final String TRUST_NAME = "trust";
    private static final String SNI_NAME = "sni";
    private static final String ALPN_NAME = "alpn";
    private static final String MUTUAL_NAME = "mutual";
    private static final String SIGNERS_NAME = "signers";
    private static final String TRUSTCACERTS_NAME = "trustcacerts";

    @Override
    public Kind kind()
    {
        return Kind.BINDING;
    }

    @Override
    public String type()
    {
        return TlsBinding.NAME;
    }

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        TlsOptionsConfig tlsOptions = (TlsOptionsConfig) options;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (tlsOptions.version != null)
        {
            object.add(VERSION_NAME, tlsOptions.version);
        }

        if (tlsOptions.keys != null)
        {
            JsonArrayBuilder keys = Json.createArrayBuilder();
            tlsOptions.keys.forEach(keys::add);
            object.add(KEYS_NAME, keys);
        }

        if (tlsOptions.trust != null)
        {
            JsonArrayBuilder trust = Json.createArrayBuilder();
            tlsOptions.trust.forEach(trust::add);
            object.add(TRUST_NAME, trust);
        }

        if (tlsOptions.trustcacerts)
        {
            object.add(TRUSTCACERTS_NAME, true);
        }

        if (tlsOptions.sni != null)
        {
            JsonArrayBuilder sni = Json.createArrayBuilder();
            tlsOptions.sni.forEach(sni::add);
            object.add(SNI_NAME, sni);
        }

        if (tlsOptions.alpn != null)
        {
            JsonArrayBuilder alpn = Json.createArrayBuilder();
            tlsOptions.alpn.forEach(alpn::add);
            object.add(ALPN_NAME, alpn);
        }

        if (tlsOptions.mutual != null &&
            (tlsOptions.trust == null || tlsOptions.mutual != REQUIRED))
        {
            String mutual = tlsOptions.mutual.name().toLowerCase();
            object.add(MUTUAL_NAME, mutual);
        }

        if (tlsOptions.signers != null)
        {
            JsonArrayBuilder signers = Json.createArrayBuilder();
            tlsOptions.signers.forEach(signers::add);
            object.add(SIGNERS_NAME, signers);
        }

        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        TlsOptionsConfigBuilder<TlsOptionsConfig> tlsOptions = TlsOptionsConfig.builder();

        if (object.containsKey(VERSION_NAME))
        {
            tlsOptions.version(object.getString(VERSION_NAME));
        }

        if (object.containsKey(KEYS_NAME))
        {
            tlsOptions.keys(asListString(object.getJsonArray(KEYS_NAME)));
        }

        if (object.containsKey(TRUST_NAME))
        {
            tlsOptions.trust(asListString(object.getJsonArray(TRUST_NAME)));
        }

        if (object.containsKey(TRUSTCACERTS_NAME))
        {
            tlsOptions.trustcacerts(object.getBoolean(TRUSTCACERTS_NAME));
        }

        if (object.containsKey(SNI_NAME))
        {
            tlsOptions.sni(asListString(object.getJsonArray(SNI_NAME)));
        }

        if (object.containsKey(ALPN_NAME))
        {
            tlsOptions.alpn(asListString(object.getJsonArray(ALPN_NAME)));
        }

        if (object.containsKey(MUTUAL_NAME))
        {
            tlsOptions.mutual(TlsMutualConfig.valueOf(object.getString(MUTUAL_NAME).toUpperCase()));
        }

        if (object.containsKey(SIGNERS_NAME))
        {
            tlsOptions.signers(asListString(object.getJsonArray(SIGNERS_NAME)));
        }

        return tlsOptions.build();
    }

    private static List<String> asListString(
        JsonArray array)
    {
        return array.stream()
            .map(TlsOptionsConfigAdapter::asString)
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
