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
package io.aklivity.zilla.runtime.engine.catalog;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.engine.validator.function.ValueConsumer;

public interface CatalogHandler
{
    int NO_SCHEMA_ID = 0;

    @FunctionalInterface
    interface Decoder
    {
        Decoder IDENTITY = (schemaId, data, index, length, next) ->
        {
            next.accept(data, index, length);
            return length;
        };

        int accept(
            int schemaId,
            DirectBuffer data,
            int index,
            int length,
            ValueConsumer next);
    }

    @FunctionalInterface
    interface Encoder
    {
        Encoder IDENTITY = (schemaId, data, index, length, next) ->
        {
            next.accept(data, index, length);
            return length;
        };

        int accept(
            int schemaId,
            DirectBuffer data,
            int index,
            int length,
            ValueConsumer next);
    }

    int register(
        String subject,
        String type,
        String schema);

    String resolve(
        int schemaId);

    int resolve(
        String subject,
        String version);

    default int resolve(
        DirectBuffer data,
        int index,
        int length)
    {
        return NO_SCHEMA_ID;
    }

    default int decode(
        DirectBuffer data,
        int index,
        int length,
        ValueConsumer next,
        Decoder decoder)
    {
        return decoder.accept(NO_SCHEMA_ID, data, index, length, next);
    }

    default int encode(
        int schemaId,
        DirectBuffer data,
        int index,
        int length,
        ValueConsumer next,
        Encoder encoder)
    {
        return encoder.accept(schemaId, data, index, length, next);
    }

    default int encodePadding()
    {
        return 0;
    }
}
