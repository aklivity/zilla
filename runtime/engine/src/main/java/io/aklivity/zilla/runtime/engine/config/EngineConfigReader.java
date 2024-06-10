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

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
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

import org.agrona.BitUtil;
import org.agrona.collections.IntArrayList;
import org.leadpony.justify.api.JsonSchema;
import org.leadpony.justify.api.JsonSchemaReader;
import org.leadpony.justify.api.JsonValidationService;
import org.leadpony.justify.api.ProblemHandler;

import io.aklivity.zilla.runtime.engine.Engine;
import io.aklivity.zilla.runtime.engine.EngineConfiguration;
import io.aklivity.zilla.runtime.engine.internal.config.NamespaceAdapter;
import io.aklivity.zilla.runtime.engine.internal.config.schema.UniquePropertyKeysSchema;
import io.aklivity.zilla.runtime.engine.resolver.Resolver;

public final class EngineConfigReader
{
    private final EngineConfiguration config;
    private final ConfigAdapterContext context;
    private final Resolver expressions;
    private final Collection<URL> schemaTypes;
    private final Consumer<String> logger;
    private final Consumer<NamespaceConfig> addResources;
    private final MessageDigest md5;

    public EngineConfigReader(
        EngineConfiguration config,
        ConfigAdapterContext context,
        Resolver expressions,
        Collection<URL> schemaTypes,
        Consumer<String> logger,
        Consumer<NamespaceConfig> addResources)
    {
        this.config = config;
        this.context = context;
        this.expressions = expressions;
        this.schemaTypes = schemaTypes;
        this.logger = logger;
        this.addResources = addResources;
        this.md5 = initMessageDigest("MD5");
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

            if (config.verboseSchemaPlain())
            {
                logSchema(schemaObject);
            }

            if (!validateAnnotatedSchema(schemaObject, schemaProvider, errors, configText))
            {
                break read;
            }

            configText = expressions.resolve(configText);

            JsonParser schemaParser = schemaProvider.createParserFactory(null)
                .createParser(new StringReader(schemaObject.toString()));

            JsonValidationService service = JsonValidationService.newInstance();
            ProblemHandler handler = service.createProblemPrinter(msg -> errors.add(new ConfigException(msg)));
            JsonSchemaReader validator = service.createSchemaReader(schemaParser);
            JsonSchema schema = new UniquePropertyKeysSchema(validator.read());

            JsonProvider provider = service.createJsonProvider(schema, parser -> handler);
            String readable = configText.stripTrailing();

            IntArrayList configsAt = new IntArrayList();
            for (int configAt = 0; configAt < readable.length(); )
            {
                configsAt.addInt(configAt);

                Reader reader = new StringReader(readable);
                reader.skip(configAt);

                try (JsonParser parser = service.createParser(reader, schema, handler))
                {
                    while (parser.hasNext())
                    {
                        parser.next();
                    }

                    configAt += (int) parser.getLocation().getStreamOffset();
                }

                if (!errors.isEmpty())
                {
                    break read;
                }
            }

            List<String> hashes = new ArrayList<>();
            for (int i = 0; i < configsAt.size(); i++)
            {
                int start = configsAt.get(i);
                int end = i < configsAt.size() - 1 ? configsAt.get(i + 1) : readable.length();
                byte[] bytes = readable.substring(start, end).stripTrailing().getBytes(StandardCharsets.UTF_8);
                String hash = calculateHash(bytes, 0, bytes.length);
                hashes.add(hash);
            }

            JsonbConfig config = new JsonbConfig()
                .withAdapters(new NamespaceAdapter(context));
            Jsonb jsonb = JsonbBuilder.newBuilder()
                .withProvider(provider)
                .withConfig(config)
                .build();

            Reader reader = new StringReader(readable);
            EngineConfigBuilder<EngineConfig> builder = EngineConfig.builder();
            for (int i = 0; i < configsAt.size(); i++)
            {
                reader.reset();
                reader.skip(configsAt.get(i));
                NamespaceConfig namespace = jsonb.fromJson(reader, NamespaceConfig.class);
                namespace.hash = hashes.get(i);
                addResources.accept(namespace);
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
        JsonProvider.provider()
            .createGeneratorFactory(singletonMap(PRETTY_PRINTING, true))
            .createGenerator(out)
            .write(schemaObject)
            .close();

        final String schemaText = out.getBuffer().toString();
        logger.accept(schemaText);
    }

    private boolean validateAnnotatedSchema(
        JsonObject schemaObject,
        JsonProvider schemaProvider,
        List<Exception> errors,
        String configText)
    {
        boolean valid = false;

        validate:
        try
        {
            final EngineConfigAnnotator annotator = new EngineConfigAnnotator();
            final JsonObject annotatedSchemaObject = annotator.annotate(schemaObject);

            if (config.verboseSchema())
            {
                logSchema(annotatedSchemaObject);
            }

            final JsonParser schemaParser = schemaProvider.createParserFactory(null)
                .createParser(new StringReader(annotatedSchemaObject.toString()));

            final JsonValidationService service = JsonValidationService.newInstance();
            ProblemHandler handler = service.createProblemPrinter(msg -> errors.add(new ConfigException(msg)));
            final JsonSchemaReader validator = service.createSchemaReader(schemaParser);
            final JsonSchema schema = new UniquePropertyKeysSchema(validator.read());

            String readable = configText.stripTrailing();

            IntArrayList configsAt = new IntArrayList();
            for (int configAt = 0; configAt < readable.length(); )
            {
                configsAt.addInt(configAt);

                Reader reader = new StringReader(readable);
                reader.skip(configAt);

                try (JsonParser parser = service.createParser(reader, schema, handler))
                {
                    while (parser.hasNext())
                    {
                        parser.next();
                    }

                    configAt += (int) parser.getLocation().getStreamOffset();
                }

                if (!errors.isEmpty())
                {
                    break validate;
                }
            }

            valid = true;
        }
        catch (IOException ex)
        {
            errors.add(ex);
        }

        return valid;

    }

    private String calculateHash(
        byte[] input,
        int offset,
        int length)
    {
        md5.reset();
        md5.update(input, offset, length);
        byte[] hash = md5.digest();
        return BitUtil.toHex(hash);
    }

    private static MessageDigest initMessageDigest(
        String algorithm)
    {
        MessageDigest messageDigest = null;
        try
        {
            messageDigest = MessageDigest.getInstance(algorithm);
        }
        catch (NoSuchAlgorithmException ex)
        {
            rethrowUnchecked(ex);
        }
        return messageDigest;
    }
}
