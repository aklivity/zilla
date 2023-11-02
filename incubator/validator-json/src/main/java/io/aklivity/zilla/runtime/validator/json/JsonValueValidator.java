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
package io.aklivity.zilla.runtime.validator.json;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.util.function.LongFunction;

import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParserFactory;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.leadpony.justify.api.JsonSchema;
import org.leadpony.justify.api.JsonSchemaReader;
import org.leadpony.justify.api.JsonValidationService;
import org.leadpony.justify.api.ProblemHandler;

import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;
import io.aklivity.zilla.runtime.engine.config.CatalogedConfig;
import io.aklivity.zilla.runtime.engine.config.SchemaConfig;
import io.aklivity.zilla.runtime.engine.validator.ValueValidator;
import io.aklivity.zilla.runtime.validator.json.config.JsonValidatorConfig;

public abstract class JsonValueValidator implements ValueValidator
{
    protected static final byte MAGIC_BYTE = 0x0;

    protected final DirectBuffer valueRO = new UnsafeBuffer();

    protected final JsonProvider schemaProvider;
    protected final JsonValidationService service;
    protected final JsonParserFactory factory;
    protected final SchemaConfig catalog;
    protected final CatalogHandler handler;
    protected final String subject;

    public JsonValueValidator(
        JsonValidatorConfig config,
        LongFunction<CatalogHandler> supplyCatalog)
    {
        this.schemaProvider = JsonProvider.provider();
        this.service = JsonValidationService.newInstance();
        this.factory = schemaProvider.createParserFactory(null);
        CatalogedConfig cataloged = config.cataloged.get(0);
        this.catalog = cataloged.schemas.size() != 0 ? cataloged.schemas.get(0) : null;
        this.handler = supplyCatalog.apply(cataloged.id);
        this.subject = catalog != null && catalog.subject != null
            ? catalog.subject
            : config.subject;
    }

    String fetchSchema(
        int schemaId)
    {
        String schema = null;
        if (schemaId > 0)
        {
            schema = handler.resolve(schemaId);
        }
        else if (catalog != null)
        {
            schemaId = handler.resolve(subject, catalog.version);
            if (schemaId > 0)
            {
                schema = handler.resolve(schemaId);
            }
        }

        return schema;
    }

    boolean validate(
        String schema,
        byte[] bytes,
        int offset,
        int length)
    {
        boolean status = false;
        try
        {
            JsonParser schemaParser = factory.createParser(new StringReader(schema));
            JsonSchemaReader reader = service.createSchemaReader(schemaParser);
            JsonSchema jsonSchema = reader.read();
            JsonProvider provider = service.createJsonProvider(jsonSchema, parser -> ProblemHandler.throwing());
            InputStream input = new ByteArrayInputStream(bytes, offset, length);
            provider.createReader(input).readObject();
            status = true;
        }
        catch (Exception e)
        {
        }
        return status;
    }
}
