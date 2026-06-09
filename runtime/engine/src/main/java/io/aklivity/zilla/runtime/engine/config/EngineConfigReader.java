/*
 * Copyright 2021-2024 Aklivity Inc.
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
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
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

import org.agrona.collections.IntArrayList;

import io.aklivity.zilla.runtime.common.json.JsonSchema;
import io.aklivity.zilla.runtime.common.json.JsonSchemaDiagnostic;
import io.aklivity.zilla.runtime.common.yaml.YamlConfig;
import io.aklivity.zilla.runtime.common.yaml.json.YamlJson;
import io.aklivity.zilla.runtime.engine.Engine;
import io.aklivity.zilla.runtime.engine.EngineConfiguration;
import io.aklivity.zilla.runtime.engine.internal.config.NamespaceAdapter;
import io.aklivity.zilla.runtime.engine.resolver.Resolver;

public final class EngineConfigReader
{
    private static final JsonProvider CONFIG_PROVIDER =
        YamlJson.provider(Map.of(YamlConfig.FEATURE_UNIQUE_KEYS, true));

    private final EngineConfiguration config;
    private final ConfigAdapterContext context;
    private final Resolver expressions;
    private final Collection<URL> schemaTypes;
    private final Consumer<String> logger;

    public EngineConfigReader(
        EngineConfiguration config,
        ConfigAdapterContext context,
        Resolver expressions,
        Collection<URL> schemaTypes,
        Consumer<String> logger)
    {
        this.config = config;
        this.context = context;
        this.expressions = expressions;
        this.schemaTypes = schemaTypes;
        this.logger = logger;
    }

    public EngineConfig read(
        String configText)
    {
        EngineConfig engine = null;

        List<Exception> errors = new LinkedList<>();

        read:
        try
        {
            InputStream schemaInput = Engine.class.getResourceAsStream("internal/schema/engine.schema.json");

            JsonProvider schemaProvider = YamlJson.provider();
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

            if (config.verboseSchemaPlain())
            {
                logSchema(schemaObject);
            }

            if (!validateAnnotatedSchema(schemaObject, errors, configText))
            {
                break read;
            }

            configText = expressions.resolve(configText);
            String readable = configText.stripTrailing();

            JsonSchema schema = JsonSchema.of(schemaObject.toString());

            IntArrayList configsAt = validateDocuments(readable, schema, errors);
            if (!errors.isEmpty())
            {
                break read;
            }

            JsonbConfig config = new JsonbConfig()
                .withAdapters(new NamespaceAdapter(context));
            Jsonb jsonb = JsonbBuilder.newBuilder()
                .withProvider(CONFIG_PROVIDER)
                .withConfig(config)
                .build();

            EngineConfigBuilder<EngineConfig> builder = EngineConfig.builder();
            for (int configAt : configsAt)
            {
                NamespaceConfig namespace = jsonb.fromJson(
                    new StringReader(readable.substring(configAt)), NamespaceConfig.class);
                namespace.configAt = configAt;
                builder.namespace(namespace);

                if (!errors.isEmpty())
                {
                    break read;
                }
            }
            engine = builder.build();
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

        return engine;
    }

    private void logSchema(
        JsonObject schemaObject)
    {
        final StringWriter out = new StringWriter();
        YamlJson.provider()
            .createGeneratorFactory(singletonMap(PRETTY_PRINTING, true))
            .createGenerator(out)
            .write(schemaObject)
            .close();

        final String schemaText = out.getBuffer().toString();
        logger.accept(schemaText);
    }

    private boolean validateAnnotatedSchema(
        JsonObject schemaObject,
        List<Exception> errors,
        String configText)
    {
        boolean valid = false;

        try
        {
            final EngineConfigAnnotator annotator = new EngineConfigAnnotator();
            final JsonObject annotatedSchemaObject = annotator.annotate(schemaObject);

            if (config.verboseSchema())
            {
                logSchema(annotatedSchemaObject);
            }

            final JsonSchema schema = JsonSchema.of(annotatedSchemaObject.toString());

            String readable = configText.stripTrailing();

            validateDocuments(readable, schema, errors);

            valid = errors.isEmpty();
        }
        catch (Exception ex)
        {
            errors.add(ex);
        }

        return valid;
    }

    private IntArrayList validateDocuments(
        String readable,
        JsonSchema schema,
        List<Exception> errors)
    {
        IntArrayList documentsAt = documentOffsets(readable);

        for (int index = 0; index < documentsAt.size(); index++)
        {
            int documentAt = documentsAt.getInt(index);
            List<JsonSchemaDiagnostic> diagnostics = new ArrayList<>();
            try (JsonParser parser = CONFIG_PROVIDER.createParser(new StringReader(readable.substring(documentAt))))
            {
                if (!schema.validate(parser, diagnostics::add))
                {
                    diagnostics.forEach(problem -> errors.add(new ConfigException(problem.toString())));
                }
            }
        }

        return documentsAt;
    }

    private IntArrayList documentOffsets(
        String readable)
    {
        IntArrayList documentsAt = new IntArrayList();

        if (!readable.isEmpty())
        {
            documentsAt.addInt(0);
        }

        try (JsonParser parser = CONFIG_PROVIDER.createParser(new StringReader(readable)))
        {
            int depth = 0;
            int documentAt = 0;
            while (parser.hasNext())
            {
                JsonParser.Event event = parser.next();
                switch (event)
                {
                case START_OBJECT, START_ARRAY ->
                    depth++;
                case END_OBJECT, END_ARRAY ->
                {
                    depth--;
                    if (depth == 0)
                    {
                        documentAt = nextDocumentAt(parser, documentsAt, documentAt);
                    }
                }
                case VALUE_STRING, VALUE_NUMBER, VALUE_TRUE, VALUE_FALSE, VALUE_NULL ->
                {
                    if (depth == 0)
                    {
                        documentAt = nextDocumentAt(parser, documentsAt, documentAt);
                    }
                }
                default ->
                {
                }
                }
            }
        }

        return documentsAt;
    }

    private int nextDocumentAt(
        JsonParser parser,
        IntArrayList documentsAt,
        int documentAt)
    {
        int nextDocumentAt = (int) parser.getLocation().getStreamOffset();
        if (nextDocumentAt <= documentAt)
        {
            throw new ConfigException("YAML parser did not advance to next document");
        }
        if (parser.hasNext())
        {
            documentsAt.addInt(nextDocumentAt);
        }
        return nextDocumentAt;
    }
}
