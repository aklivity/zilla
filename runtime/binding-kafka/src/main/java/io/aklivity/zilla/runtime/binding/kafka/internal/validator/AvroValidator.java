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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

import org.agrona.DirectBuffer;
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
    private Map<String, CatalogHandler> handler;
    private Map<Integer, Schema> schemas;


    public AvroValidator(
        AvroValidatorConfig config,
        ToLongFunction<String> resolveId,
        LongFunction<CatalogHandler> supplyCatalog)
    {
        this.catalogs = config.catalogList;
        this.schemas = new HashMap<>();
        this.handler = resolveId != null &&
            supplyCatalog != null ? catalogs.stream()
            .collect(Collectors.toMap(c -> c.name, c -> supplyCatalog.apply(resolveId.applyAsLong(c.name)))) : null;
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

        if (!schemas.containsKey(schemaId))
        {
            schemas.put(schemaId, new Schema.Parser().parse(handler.get(catalogs.get(0).name).resolve(schemaId)));
        }

        int valLength = length - 1 - 4;
        byte[] valBytes = new byte[valLength];
        data.getBytes(length - valLength, valBytes);

        try
        {
            DatumReader reader = new GenericDatumReader(schemas.get(schemaId));
            DecoderFactory decoderFactory = DecoderFactory.get();
            reader.read(null, decoderFactory.binaryDecoder(valBytes, null));
            return true;
        }
        catch (Exception e)
        {
            return false;
        }
    }
}
