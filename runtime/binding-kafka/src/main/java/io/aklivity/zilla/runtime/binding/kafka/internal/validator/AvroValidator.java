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

import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.LongFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

import org.agrona.DirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DecoderFactory;

import io.aklivity.zilla.runtime.binding.kafka.internal.config.KafkaCatalogConfig;
import io.aklivity.zilla.runtime.binding.kafka.internal.validator.config.AvroValidatorConfig;
import io.aklivity.zilla.runtime.engine.catalog.CatalogHandler;

public final class AvroValidator implements Validator
{
    private static final byte MAGIC_BYTE = 0x0;

    private final List<KafkaCatalogConfig> catalogs;
    private final Long2ObjectHashMap<CatalogHandler> handlersById;
    private final DecoderFactory decoder;
    private DatumReader reader;

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
    }

    @Override
    public boolean validate(
        DirectBuffer data,
        int index,
        int length)
    {
        byte[] payloadBytes = new byte[length];
        data.getBytes(0, payloadBytes);
        ByteBuffer byteBuf = ByteBuffer.wrap(payloadBytes);

        if (byteBuf.get() != MAGIC_BYTE)
        {
            System.out.println("Unknown magic byte!");
            return false;
        }

        int schemaId = byteBuf.getInt();
        int valLength = length - 1 - 4;
        byte[] valBytes = new byte[valLength];
        data.getBytes(length - valLength, valBytes);

        try
        {
            reader = new GenericDatumReader(new Schema.Parser().parse(
                handlersById.get(catalogs.get(0).id).resolve(schemaId)));
            reader.read(null, decoder.binaryDecoder(valBytes, null));
            return true;
        }
        catch (Exception e)
        {
            return false;
        }
    }
}
