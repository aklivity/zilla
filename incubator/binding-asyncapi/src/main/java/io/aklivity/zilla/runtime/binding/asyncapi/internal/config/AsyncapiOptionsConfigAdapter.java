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

import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonStructure;
import jakarta.json.JsonValue;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.adapter.JsonbAdapter;

import org.leadpony.justify.api.JsonSchema;
import org.leadpony.justify.api.JsonValidationService;
import org.leadpony.justify.api.ProblemHandler;

import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.asyncapi.config.AsyncapiOptionsConfigBuilder;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.AsyncapiBinding;
import io.aklivity.zilla.runtime.binding.asyncapi.internal.model.Asyncapi;
import io.aklivity.zilla.runtime.binding.tcp.config.TcpOptionsConfig;
import io.aklivity.zilla.runtime.binding.tls.config.TlsOptionsConfig;
import io.aklivity.zilla.runtime.engine.config.ConfigAdapterContext;
import io.aklivity.zilla.runtime.engine.config.ConfigException;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapter;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;

public final class AsyncapiOptionsConfigAdapter implements OptionsConfigAdapterSpi, JsonbAdapter<OptionsConfig, JsonObject>
{
    private static final String SPECS_NAME = "specs";
    private static final String TCP_NAME = "tcp";
    private static final String TLS_NAME = "tls";

    private OptionsConfigAdapter tcpOptions;
    private OptionsConfigAdapter tlsOptions;
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

        if (asyncapiOptions.tcp != null)
        {
            final TcpOptionsConfig tcp = asyncapiOptions.tcp;
            object.add(TCP_NAME, tcpOptions.adaptToJson(tcp));
        }

        if (asyncapiOptions.tls != null)
        {
            final TlsOptionsConfig tls = asyncapiOptions.tls;
            object.add(TLS_NAME, tlsOptions.adaptToJson(tls));
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

        if (object.containsKey(TCP_NAME))
        {
            final JsonObject tcp = object.getJsonObject(TCP_NAME);
            final TcpOptionsConfig tcpOptions = (TcpOptionsConfig) this.tcpOptions.adaptFromJson(tcp);
            asyncapiOptions.tcp(tcpOptions);
        }

        if (object.containsKey(TLS_NAME))
        {
            final JsonObject tls = object.getJsonObject(TLS_NAME);
            final TlsOptionsConfig tlsOptions = (TlsOptionsConfig) this.tlsOptions.adaptFromJson(tls);
            asyncapiOptions.tls(tlsOptions);
        }

        return asyncapiOptions.build();
    }

    @Override
    public void adaptContext(
        ConfigAdapterContext context)
    {
        this.readURL = context::readURL;
        this.tcpOptions = new OptionsConfigAdapter(Kind.BINDING, context);
        this.tcpOptions.adaptType("tcp");
        this.tlsOptions = new OptionsConfigAdapter(Kind.BINDING, context);
        this.tlsOptions.adaptType("tls");
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
        if (validateAsyncapiSchema(asyncapiText))
        {
            try (Jsonb jsonb = JsonbBuilder.create())
            {
                asyncapi = jsonb.fromJson(asyncapiText, Asyncapi.class);
            }
            catch (Exception ex)
            {
                rethrowUnchecked(ex);
            }
        }
        return asyncapi;
    }

    private boolean validateAsyncapiSchema(
        String asyncapiText)
    {
        List<Exception> errors = new LinkedList<>();

        boolean valid = false;

        try
        {
            JsonValidationService service = JsonValidationService.newInstance();

            String version = detectAsyncapiVersion(asyncapiText);
            InputStream schemaInput = selectSchemaPathForVersion(version);

            JsonSchema schema = service.readSchema(schemaInput);
            ProblemHandler handler = service.createProblemPrinter(msg -> errors.add(new ConfigException(msg)));

            String readable = asyncapiText.stripTrailing();
            Reader asyncapiReader = new StringReader(readable);

            JsonReader reader = service.createReader(asyncapiReader, schema, handler);

            JsonStructure json = reader.read();
            valid = json != null;
        }
        catch (Exception ex)
        {
            errors.add(ex);
        }

        return valid;
    }

    private String detectAsyncapiVersion(
        String asyncapiText)
    {
        try (JsonReader reader = Json.createReader(new StringReader(asyncapiText)))
        {
            JsonObject json = reader.readObject();
            if (json.containsKey("asyncapi"))
            {
                return json.getString("asyncapi");
            }
            else
            {
                throw new IllegalArgumentException("Unable to determine AsyncAPI version.");
            }
        }
        catch (Exception e)
        {
            throw new RuntimeException("Error reading AsyncAPI document.", e);
        }
    }

    private InputStream selectSchemaPathForVersion(
        String version)
    {
        if (version.startsWith("3.0"))
        {
            return AsyncapiBinding.class.getResourceAsStream("schema/asyncapi.3.0.schema.json");
        }
        else if (version.startsWith("2.6"))
        {
            return AsyncapiBinding.class.getResourceAsStream("schema/asyncapi.2.6.schema.json");
        }
        else
        {
            throw new IllegalArgumentException("Unsupported AsyncAPI version: " + version);
        }
    }
}
