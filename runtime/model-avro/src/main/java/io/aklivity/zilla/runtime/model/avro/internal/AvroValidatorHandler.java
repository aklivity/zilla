/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.model.avro.internal;

import java.io.IOException;
import java.io.InputStream;

import org.agrona.DirectBuffer;
import org.agrona.ExpandableDirectByteBuffer;
import org.apache.avro.AvroRuntimeException;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.model.ValidatorHandler;
import io.aklivity.zilla.runtime.engine.model.function.ValueConsumer;
import io.aklivity.zilla.runtime.model.avro.config.AvroModelConfig;

public class AvroValidatorHandler extends AvroModelHandler implements ValidatorHandler
{
    private final ExpandableDirectByteBuffer buffer;

    public AvroValidatorHandler(
        AvroModelConfiguration config,
        AvroModelConfig options,
        EngineContext context)
    {
        super(config, options, context);
        this.buffer = new ExpandableDirectByteBuffer();
    }

    @Override
    public boolean validate(
        long traceId,
        long bindingId,
        int flags,
        DirectBuffer data,
        int index,
        int length,
        ValueConsumer next)
    {
        boolean status = true;

        try
        {
            if ((flags & FLAGS_INIT) != 0x00)
            {
                this.progress = 0;
            }

            buffer.putBytes(progress, data, index, length);
            progress += length;

            if ((flags & FLAGS_FIN) != 0x00)
            {
                if (catalog != null && "encoded".equals(catalog.strategy))
                {
                    status = handler.validate(traceId, bindingId, buffer, 0, progress, next, this::validatePayload);
                }
                else
                {
                    in.wrap(buffer, 0, progress);

                    int schemaId = catalog != null && catalog.id > 0
                        ? catalog.id
                        : handler.resolve(subject, catalog.version);

                    status = validate(traceId, bindingId, schemaId, in);
                }
            }
        }
        catch (Exception ex)
        {
            status = false;
            event.validationFailure(traceId, bindingId, ex.getMessage());
        }

        return status;
    }

    private boolean validatePayload(
        long traceId,
        long bindingId,
        int schemaId,
        DirectBuffer data,
        int index,
        int length,
        ValueConsumer next)
    {
        in.wrap(data, index, length);
        return validate(traceId, bindingId, schemaId, in);
    }

    private boolean validate(
        long traceId,
        long bindingId,
        int schemaId,
        InputStream in)
    {
        boolean status = false;
        try
        {
            Schema schema = supplySchema(schemaId);
            if (schema != null)
            {
                GenericRecord record = supplyRecord(schemaId);
                GenericDatumReader<GenericRecord> reader = supplyReader(schemaId);
                if (reader != null)
                {
                    decoderFactory.binaryDecoder(in, decoder);
                    reader.read(record, decoder);
                    status = true;
                }
            }
        }
        catch (IOException | AvroRuntimeException ex)
        {
            event.validationFailure(traceId, bindingId, ex.getMessage());
        }
        return status;
    }
}
