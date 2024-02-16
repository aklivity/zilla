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

import static org.agrona.LangUtil.rethrowUnchecked;

import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

import jakarta.json.Json;
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

import io.aklivity.zilla.runtime.binding.http.config.HttpOptionsConfig;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiConfig;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenapiOptionsConfig;
import io.aklivity.zilla.runtime.binding.openapi.config.OpenpaiOptionsConfigBuilder;
import io.aklivity.zilla.runtime.binding.openapi.internal.OpenapiBinding;
import io.aklivity.zilla.runtime.binding.openapi.internal.model.OpenApi;
import io.aklivity.zilla.runtime.binding.tcp.config.TcpOptionsConfig;
import io.aklivity.zilla.runtime.binding.tls.config.TlsOptionsConfig;
import io.aklivity.zilla.runtime.engine.config.ConfigAdapterContext;
import io.aklivity.zilla.runtime.engine.config.ConfigException;
import io.aklivity.zilla.runtime.engine.config.OptionsConfig;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapter;
import io.aklivity.zilla.runtime.engine.config.OptionsConfigAdapterSpi;

public final class OpenapiOptionsConfigAdapter implements OptionsConfigAdapterSpi, JsonbAdapter<OptionsConfig, JsonObject>
{
    private static final String TCP_NAME = "tcp";
    private static final String TLS_NAME = "tls";
    private static final String HTTP_NAME = "http";
    private static final String SPECS_NAME = "specs";
    private OptionsConfigAdapter tcpOptions;
    private OptionsConfigAdapter tlsOptions;
    private OptionsConfigAdapter httpOptions;
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

        if (openOptions.tcp != null)
        {
            final TcpOptionsConfig tcp = ((OpenapiOptionsConfig) options).tcp;
            object.add(TCP_NAME, tcpOptions.adaptToJson(tcp));
        }

        if (openOptions.tls != null)
        {
            final TlsOptionsConfig tls = ((OpenapiOptionsConfig) options).tls;
            object.add(TLS_NAME, tlsOptions.adaptToJson(tls));
        }

        HttpOptionsConfig http = openOptions.http;
        if (http != null)
        {
            object.add(HTTP_NAME, httpOptions.adaptToJson(http));
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

        if (object.containsKey(TCP_NAME))
        {
            final JsonObject tcp = object.getJsonObject(TCP_NAME);
            final TcpOptionsConfig tcpOptions = (TcpOptionsConfig) this.tcpOptions.adaptFromJson(tcp);
            openapiOptions.tcp(tcpOptions);
        }

        if (object.containsKey(TLS_NAME))
        {
            final JsonObject tls = object.getJsonObject(TLS_NAME);
            final TlsOptionsConfig tlsOptions = (TlsOptionsConfig) this.tlsOptions.adaptFromJson(tls);
            openapiOptions.tls(tlsOptions);
        }

        if (object.containsKey(HTTP_NAME))
        {
            JsonObject http = object.getJsonObject(HTTP_NAME);

            final HttpOptionsConfig httpOptions = (HttpOptionsConfig) this.httpOptions.adaptFromJson(http);
            openapiOptions.http(httpOptions);
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
        this.tcpOptions = new OptionsConfigAdapter(Kind.BINDING, context);
        this.tcpOptions.adaptType("tcp");
        this.tlsOptions = new OptionsConfigAdapter(Kind.BINDING, context);
        this.tlsOptions.adaptType("tls");
        this.httpOptions = new OptionsConfigAdapter(Kind.BINDING, context);
        this.httpOptions.adaptType("http");
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
        if (validateOpenapiSchema(openapiText))
        {
            try (Jsonb jsonb = JsonbBuilder.create())
            {
                openApi = jsonb.fromJson(openapiText, OpenApi.class);
            }
            catch (Exception ex)
            {
                rethrowUnchecked(ex);
            }
        }
        return openApi;
    }

    private boolean validateOpenapiSchema(
        String openapiText)
    {
        List<Exception> errors = new LinkedList<>();

        boolean valid = false;

        try
        {
            JsonValidationService service = JsonValidationService.newInstance();

            String openApiVersion = detectOpenApiVersion(openapiText);
            InputStream schemaInput = selectSchemaPathForVersion(openApiVersion);

            JsonSchema schema = service.readSchema(schemaInput);
            ProblemHandler handler = service.createProblemPrinter(msg -> errors.add(new ConfigException(msg)));

            String readable = openapiText.stripTrailing();
            Reader openapiReader = new StringReader(readable);

            JsonReader reader = service.createReader(openapiReader, schema, handler);

            JsonStructure json = reader.read();
            valid = json != null;
        }
        catch (Exception ex)
        {
            errors.add(ex);
        }

        return valid;
    }

    private String detectOpenApiVersion(
        String openapiText)
    {
        try (JsonReader reader = Json.createReader(new StringReader(openapiText)))
        {
            JsonObject json = reader.readObject();
            if (json.containsKey("openapi"))
            {
                return json.getString("openapi");
            }
            else
            {
                throw new IllegalArgumentException("Unable to determine OpenAPI version.");
            }
        }
        catch (Exception e)
        {
            throw new RuntimeException("Error reading OpenAPI document.", e);
        }
    }

    private InputStream selectSchemaPathForVersion(
        String version)
    {
        if (version.startsWith("3.0"))
        {
            return OpenapiBinding.class.getResourceAsStream("schema/openapi.3.0.schema.json");
        }
        else if (version.startsWith("3.1"))
        {
            return OpenapiBinding.class.getResourceAsStream("schema/openapi.3.1.schema.json");
        }
        else
        {
            throw new IllegalArgumentException("Unsupported OpenAPI version: " + version);
        }
    }
}
