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
package io.aklivity.zilla.runtime.binding.asyncapi.internal.config;

import static java.util.stream.Collectors.toList;
import static org.agrona.LangUtil.rethrowUnchecked;

import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiOptionsConfigBuilder;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.AsyncapiBinding;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.Asyncapi;
import io.aklivity.zilla.runtime.binding.tcp.config.TcpOptionsConfig;
import io.aklivity.zilla.runtime.binding.tcp.config.TcpOptionsConfigBuilder;
import io.aklivity.zilla.runtime.binding.tls.internal.config.TlsOptionsConfigAdapter;
import io.aklivity.zilla.runtime.engine.config.ConfigAdapterContext;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;

import org.agrona.collections.IntHashSet;
import org.agrona.collections.MutableInteger;

public final class AsyncapiOptionsConfigAdapter implements OptionsConfigAdapterSpi, JsonbAdapter<OptionsConfig, JsonObject>
{
    private static final String SPECS_NAME = "specs";
    private static final String HOST_NAME = "host";
    private static final String PORT_NAME = "port";
    private static final String KEYS_NAME = "keys";
    private static final String TRUST_NAME = "trust";
    private static final String SNI_NAME = "sni";
    private static final String ALPN_NAME = "alpn";
    private static final String TRUSTCACERTS_NAME = "trustcacerts";

    private Function<String, String> readURL;

    public Kind kind()
    {
        return Kind.BINDING;
    }

    @Override
    public String type()
    {
        return AsyncapiBinding.NAME;
    }

    @Override
    public JsonObject adaptToJson(
        OptionsConfig options)
    {
        AsyncapiOptionsConfig asyncapiOptions = (AsyncapiOptionsConfig) options;

        JsonObjectBuilder object = Json.createObjectBuilder();

        if (asyncapiOptions.specs != null)
        {
            JsonArrayBuilder keys = Json.createArrayBuilder();
            asyncapiOptions.specs.forEach(p -> keys.add(p.location));
            object.add(SPECS_NAME, keys);
        }

        object.add(HOST_NAME, asyncapiOptions.host);

        if (asyncapiOptions.ports != null)
        {
            if (asyncapiOptions.ports.length == 1)
            {
                object.add(PORT_NAME, asyncapiOptions.ports[0]);
            }
            else
            {
                JsonArrayBuilder ports = Json.createArrayBuilder();
                for (int port : asyncapiOptions.ports)
                {
                    ports.add(port);
                }

                object.add(PORT_NAME, ports);
            }
        }

        if (asyncapiOptions.keys != null)
        {
            JsonArrayBuilder keys = Json.createArrayBuilder();
            asyncapiOptions.keys.forEach(keys::add);
            object.add(KEYS_NAME, keys);
        }

        if (asyncapiOptions.trust != null)
        {
            JsonArrayBuilder trust = Json.createArrayBuilder();
            asyncapiOptions.trust.forEach(trust::add);
            object.add(TRUST_NAME, trust);
        }

        if (asyncapiOptions.trust != null && asyncapiOptions.trustcacerts ||
            asyncapiOptions.trust == null && !asyncapiOptions.trustcacerts)
        {
            object.add(TRUSTCACERTS_NAME, asyncapiOptions.trustcacerts);
        }

        if (asyncapiOptions.sni != null)
        {
            JsonArrayBuilder sni = Json.createArrayBuilder();
            asyncapiOptions.sni.forEach(sni::add);
            object.add(SNI_NAME, sni);
        }

        if (asyncapiOptions.alpn != null)
        {
            JsonArrayBuilder alpn = Json.createArrayBuilder();
            asyncapiOptions.alpn.forEach(alpn::add);
            object.add(ALPN_NAME, alpn);
        }

        return object.build();
    }

    @Override
    public OptionsConfig adaptFromJson(
        JsonObject object)
    {
        final AsyncapiOptionsConfigBuilder<AsyncapiOptionsConfig> asyncapiOptions = AsyncapiOptionsConfig.builder();

        List<AsyncapiConfig> specs = object.containsKey(SPECS_NAME)
            ? asListAsyncapis(object.getJsonArray(SPECS_NAME))
            : null;
        asyncapiOptions.specs(specs);

        asyncapiOptions.host(object.getString(HOST_NAME));

        JsonValue portsValue = object.get(PORT_NAME);
        IntHashSet portsSet = new IntHashSet();
        switch (portsValue.getValueType())
        {
        case ARRAY:
            JsonArray portsArray = portsValue.asJsonArray();
            portsArray.forEach(value -> adaptPortsValueFromJson(value, portsSet));
            break;
        default:
            adaptPortsValueFromJson(portsValue, portsSet);
            break;
        }

        int[] ports = new int[portsSet.size()];
        MutableInteger index = new MutableInteger();
        portsSet.forEach(i -> ports[index.value++] = i);
        asyncapiOptions.ports(ports);

        if (object.containsKey(KEYS_NAME))
        {
            asyncapiOptions.keys(asListString(object.getJsonArray(KEYS_NAME)));
        }

        if (object.containsKey(TRUST_NAME))
        {
            asyncapiOptions.trust(asListString(object.getJsonArray(TRUST_NAME)));
        }

        if (object.containsKey(TRUSTCACERTS_NAME))
        {
            asyncapiOptions.trustcacerts(object.getBoolean(TRUSTCACERTS_NAME));
        }

        if (object.containsKey(SNI_NAME))
        {
            asyncapiOptions.sni(asListString(object.getJsonArray(SNI_NAME)));
        }

        if (object.containsKey(ALPN_NAME))
        {
            asyncapiOptions.alpn(asListString(object.getJsonArray(ALPN_NAME)));
        }

        return asyncapiOptions.build();
    }

    @Override
    public void adaptContext(
        ConfigAdapterContext context)
    {
        this.readURL = context::readURL;
    }

    private List<AsyncapiConfig> asListAsyncapis(
        JsonArray array)
    {
        return array.stream()
            .map(this::asAsyncapi)
            .collect(toList());
    }

    private AsyncapiConfig asAsyncapi(
        JsonValue value)
    {
        final String location = ((JsonString) value).getString();
        final String specText = readURL.apply(location);
        Asyncapi asyncapi = parseAsyncapi(specText);

        return new AsyncapiConfig(location, asyncapi);
    }

    private Asyncapi parseAsyncapi(
        String asyncapiText)
    {
        Asyncapi asyncapi = null;
        try (Jsonb jsonb = JsonbBuilder.create())
        {
            asyncapi = jsonb.fromJson(asyncapiText, Asyncapi.class);
        }
        catch (Exception ex)
        {
            rethrowUnchecked(ex);
        }
        return asyncapi;
    }

    private static void adaptPortsValueFromJson(
        JsonValue value,
        IntHashSet ports)
    {
        switch (value.getValueType())
        {
        case STRING:
        {
            String port = ((JsonString) value).getString();
            int dashAt = port.indexOf('-');
            if (dashAt != -1)
            {
                int portRangeLow = Integer.parseInt(port.substring(0, dashAt));
                int portRangeHigh = Integer.parseInt(port.substring(dashAt + 1));
                IntStream.range(portRangeLow, portRangeHigh + 1).forEach(ports::add);
            }
            else
            {
                ports.add(Integer.parseInt(port));
            }
            break;
        }
        case NUMBER:
        default:
        {
            int port = ((JsonNumber) value).intValue();
            ports.add(port);
            break;
        }
        }
    }

    private static List<String> asListString(
        JsonArray array)
    {
        return array.stream()
            .map(AsyncapiOptionsConfigAdapter::asString)
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
