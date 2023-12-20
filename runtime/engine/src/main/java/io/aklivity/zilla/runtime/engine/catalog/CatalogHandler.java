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

import io.aklivity.zilla.runtime.engine.config.SchemaConfig;
import io.aklivity.zilla.runtime.engine.validator.function.ValueConsumer;

public interface CatalogHandler
{
    int NO_SCHEMA_ID = 0;

    @FunctionalInterface
    interface Read
    {
        int accept(
            DirectBuffer data,
            int payloadIndex,
            int payloadLength,
            ValueConsumer next,
            int schemaId);
    }

    @FunctionalInterface
    interface Write
    {
        void accept(
            DirectBuffer data,
            int payloadIndex,
            int payloadLength);
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

    int resolve(
        DirectBuffer data,
        int index,
        int length,
        SchemaConfig catalog,
        String subject);

    int decode(
        DirectBuffer data,
        int index,
        int length,
        ValueConsumer next,
        SchemaConfig catalog,
        String subject,
        Read read);

    int encode(
        DirectBuffer data,
        int index,
        int length,
        ValueConsumer next,
        int schemaId,
        Write write);

    default int maxPadding()
    {
        return 0;
    }
}
