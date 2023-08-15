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
package io.aklivity.zilla.runtime.engine.config;

import static jakarta.json.stream.JsonGenerator.PRETTY_PRINTING;
import static java.util.Collections.singletonMap;
import static org.agrona.LangUtil.rethrowUnchecked;

import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URL;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonPatch;
import jakarta.json.JsonReader;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonParser;

import org.leadpony.justify.api.JsonSchema;
import org.leadpony.justify.api.JsonSchemaReader;
import org.leadpony.justify.api.JsonValidationService;
import org.leadpony.justify.api.ProblemHandler;

import io.aklivity.zilla.runtime.engine.Engine;
import io.aklivity.zilla.runtime.engine.internal.config.NamespaceAdapter;
import io.aklivity.zilla.runtime.engine.internal.config.schema.UniquePropertyKeysSchema;

public final class ConfigReader
{
    private final ConfigAdapterContext context;
    private final Collection<URL> schemaTypes;
    private final Consumer<String> logger;

    public ConfigReader(
        ConfigAdapterContext context,
        Collection<URL> schemaTypes,
        Consumer<String> logger)
    {
        this.context = context;
        this.schemaTypes = schemaTypes;
        this.logger = logger;
    }

    public NamespaceConfig read(
        Reader reader)
    {
        NamespaceConfig namespace = null;

        List<Exception> errors = new LinkedList<>();

        read:
        try
        {
            InputStream schemaInput = Engine.class.getResourceAsStream("internal/schema/engine.schema.json");

            JsonProvider schemaProvider = JsonProvider.provider();
            JsonReader schemaReader = schemaProvider.createReader(schemaInput);
            JsonObject schemaObject = schemaReader.readObject();

            for (URL schemaType : schemaTypes)
            {
                InputStream schemaPatchInput = schemaType.openStream();
                JsonReader schemaPatchReader = schemaProvider.createReader(schemaPatchInput);
                JsonArray schemaPatchArray = schemaPatchReader.readArray();
                JsonPatch schemaPatch = schemaProvider.createPatch(schemaPatchArray);

                schemaObject = schemaPatch.apply(schemaObject);
            }

            if (logger != null)
            {
                final StringWriter out = new StringWriter();
                schemaProvider.createGeneratorFactory(singletonMap(PRETTY_PRINTING, true))
                    .createGenerator(out)
                    .write(schemaObject)
                    .close();

                final String schemaText = out.getBuffer().toString();
                logger.accept(schemaText);
            }

            JsonParser schemaParser = schemaProvider.createParserFactory(null)
                .createParser(new StringReader(schemaObject.toString()));

            JsonValidationService service = JsonValidationService.newInstance();
            ProblemHandler handler = service.createProblemPrinter(msg -> errors.add(new ConfigException(msg)));
            JsonSchemaReader validator = service.createSchemaReader(schemaParser);
            JsonSchema schema = new UniquePropertyKeysSchema(validator.read());

            JsonProvider provider = service.createJsonProvider(schema, parser -> handler);
            provider.createReader(reader).read();

            if (!errors.isEmpty())
            {
                break read;
            }

            JsonbConfig config = new JsonbConfig()
                .withAdapters(new NamespaceAdapter(context));
            Jsonb jsonb = JsonbBuilder.newBuilder()
                .withProvider(provider)
                .withConfig(config)
                .build();

            reader.reset();
            namespace = jsonb.fromJson(reader, NamespaceConfig.class);

            if (!errors.isEmpty())
            {
                break read;
            }
        }
        catch (Exception ex)
        {
            errors.add(ex);
        }

        if (!errors.isEmpty())
        {
            Exception ex = errors.remove(0);
            errors.forEach(ex::addSuppressed);
            rethrowUnchecked(ex);
        }

        return namespace;
    }
}
