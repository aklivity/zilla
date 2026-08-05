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
package io.aklivity.zilla.config.engine;

import static jakarta.json.stream.JsonGenerator.PRETTY_PRINTING;
import static java.util.Collections.singletonMap;
import static org.agrona.LangUtil.rethrowUnchecked;

import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonPatch;
import jakarta.json.JsonReader;
import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonParser;

import io.aklivity.zilla.runtime.common.json.JsonSchema;
import io.aklivity.zilla.runtime.common.yaml.YamlConfig;
import io.aklivity.zilla.runtime.common.yaml.json.YamlJson;

public final class EngineConfigReader
{
    private static final JsonProvider CONFIG_PROVIDER =
        YamlJson.provider(Map.of(YamlConfig.FEATURE_UNIQUE_KEYS, true));

    private final UnaryOperator<String> resolver;
    private final EngineInfo info;
    private final Consumer<String> schemaLogger;
    private final Consumer<String> annotatedSchemaLogger;

    public EngineConfigReader(
        UnaryOperator<String> resolver,
        EngineInfo info,
        Consumer<String> schemaLogger,
        Consumer<String> annotatedSchemaLogger)
    {
        this.resolver = resolver;
        this.info = info;
        this.schemaLogger = schemaLogger;
        this.annotatedSchemaLogger = annotatedSchemaLogger;
    }

    public EngineConfig read(
        String configText)
    {
        EngineConfig engine = null;

        List<Exception> errors = new LinkedList<>();

        read:
        try
        {
            InputStream schemaInput = info.schema().openStream();

            JsonProvider schemaProvider = YamlJson.provider();
            JsonReader schemaReader = schemaProvider.createReader(schemaInput);
            JsonObject schemaObject = schemaReader.readObject();

            for (URL schemaType : info.patches())
            {
                InputStream schemaPatchInput = schemaType.openStream();
                JsonReader schemaPatchReader = schemaProvider.createReader(schemaPatchInput);
                JsonArray schemaPatchArray = schemaPatchReader.readArray();
                JsonPatch schemaPatch = schemaProvider.createPatch(schemaPatchArray);

                schemaObject = schemaPatch.apply(schemaObject);
            }

            logSchema(schemaObject, schemaLogger);

            if (!validateAnnotatedSchema(schemaObject, errors, configText))
            {
                break read;
            }

            configText = resolver.apply(configText);
            String readable = configText.stripTrailing();

            JsonSchema schema = JsonSchema.of(schemaObject.toString());

            NamespaceConfigReader namespaces = new NamespaceConfigReader(info);

            EngineConfigBuilder<EngineConfig> builder = EngineConfig.builder();

            readDocuments(readable, schema, namespaces, builder, errors);

            if (!errors.isEmpty())
            {
                break read;
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
        JsonObject schemaObject,
        Consumer<String> logger)
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

            logSchema(annotatedSchemaObject, annotatedSchemaLogger);

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

    // JsonSchema.validate() stops exactly at each document boundary (top-level structural depth
    // returning to zero), so one shared parser can validate every document in the stream in turn
    private void validateDocuments(
        String readable,
        JsonSchema schema,
        List<Exception> errors)
    {
        try (JsonParser parser = CONFIG_PROVIDER.createParser(new StringReader(readable)))
        {
            while (parser.hasNext())
            {
                schema.validate(parser, problem -> errors.add(new ConfigException(problem.toString())));
            }
        }
    }

    // same per-document stopping as validateDocuments, but each namespace is parsed from its own
    // document text as soon as that document validates clean, instead of pre-computing every
    // document's offset before parsing any of them
    private void readDocuments(
        String readable,
        JsonSchema schema,
        NamespaceConfigReader namespaces,
        EngineConfigBuilder<EngineConfig> builder,
        List<Exception> errors)
    {
        try (JsonParser parser = CONFIG_PROVIDER.createParser(new StringReader(readable)))
        {
            int documentAt = 0;
            while (parser.hasNext())
            {
                schema.validate(parser, problem -> errors.add(new ConfigException(problem.toString())));

                if (errors.isEmpty())
                {
                    NamespaceConfig namespace = namespaces.read(readable.substring(documentAt));
                    namespace.configAt = documentAt;
                    builder.namespace(namespace);
                }

                documentAt = parser.hasNext() ? (int) parser.getLocation().getStreamOffset() : readable.length();
            }
        }
    }
}
