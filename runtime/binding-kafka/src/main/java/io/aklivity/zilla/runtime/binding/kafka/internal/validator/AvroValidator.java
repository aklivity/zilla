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
package io.aklivity.zilla.runtime.binding.kafka.internal.validator;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.LongFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

import org.agrona.DirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;

import io.aklivity.zilla.runtime.binding.kafka.internal.config.KafkaCatalogConfig;
import io.aklivity.zilla.runtime.binding.kafka.internal.validator.avro.Schema;
import io.aklivity.zilla.runtime.binding.kafka.internal.validator.avro.Schema.Parser;
import io.aklivity.zilla.runtime.binding.kafka.internal.validator.avro.generic.GenericDatumReader;
import io.aklivity.zilla.runtime.binding.kafka.internal.validator.avro.io.DatumReader;
import io.aklivity.zilla.runtime.binding.kafka.internal.validator.avro.io.DecoderFactory;
import io.aklivity.zilla.runtime.binding.kafka.internal.validator.config.AvroValidatorConfig;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;

public final class AvroValidator implements Validator
{
    private static final byte MAGIC_BYTE = 0x0;

    private final List<KafkaCatalogConfig> catalogs;
    private final Long2ObjectHashMap<CatalogHandler> handlersById;
    private final CatalogHandler handler;
    private final DecoderFactory decoder;
    private DatumReader reader;
    private Parser parser;

    public AvroValidator(
        AvroValidatorConfig config,
        ToLongFunction<String> resolveId,
        LongFunction<CatalogHandler> supplyCatalog)
    {
        this.handlersById = new Long2ObjectHashMap<>();
        this.decoder = DecoderFactory.get();
        this.catalogs = config.catalogs.stream().map(c ->
        {
            c.id = resolveId.applyAsLong(c.name);
            handlersById.put(c.id, supplyCatalog.apply(c.id));
            return c;
        }).collect(Collectors.toList());
        this.handler = handlersById.get(catalogs.get(0).id);
        this.parser = new Schema.Parser();
    }

    @Override
    public boolean validate(
        DirectBuffer data,
        int index,
        int length)
    {
        boolean status = false;
        byte[] payloadBytes = new byte[length];
        data.getBytes(0, payloadBytes);
        ByteBuffer byteBuf = ByteBuffer.wrap(payloadBytes);

        tryValidate:
        try
        {
            if (byteBuf.get() != MAGIC_BYTE)
            {
                System.out.println("Unknown magic byte!");
                break tryValidate;
            }

            int schemaId = byteBuf.getInt();
            int valLength = length - 1 - 4;
            byte[] valBytes = new byte[valLength];
            data.getBytes(length - valLength, valBytes);

            reader = new GenericDatumReader(parser.parse(handler.resolve(schemaId)));
            reader.read(null, decoder.binaryDecoder(valBytes, null));
            status = true;
        }
        catch (IOException e)
        {
        }
        return status;
    }
}
