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

import org.agrona.DirectBuffer;
import org.agrona.ExpandableDirectByteBuffer;

import io.aklivity.zilla.runtime.engine.EngineContext;
import io.aklivity.zilla.runtime.engine.model.ValidatorHandler;
import io.aklivity.zilla.runtime.engine.model.function.ValueConsumer;
import io.aklivity.zilla.runtime.model.avro.config.AvroModelConfig;

public class AvroValidatorHandler extends AvroModelHandler implements ValidatorHandler
{
    private final ExpandableDirectByteBuffer buffer;

    private int progress;

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
                    int schemaId = catalog != null && catalog.id > 0
                        ? catalog.id
                        : handler.resolve(subject, catalog.version);

                    status = validate(traceId, bindingId, schemaId, buffer, 0, progress);
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
        return validate(traceId, bindingId, schemaId, data, index, length);
    }
}
