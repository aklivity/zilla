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
package io.aklivity.zilla.runtime.engine.model;

import org.agrona.DirectBuffer;

import io.aklivity.zilla.runtime.engine.model.function.ValueConsumer;

public interface ConverterHandler
{
    int FLAGS_COMPLETE = 0x03;
    int VALIDATION_FAILURE = -1;

    ConverterHandler NONE = (traceId, bindingId, data, index, length, next) ->
    {
        next.accept(data, index, length);
        return length;
    };

    int convert(
        long traceId,
        long bindingId,
        DirectBuffer data,
        int index,
        int length,
        ValueConsumer next);

    default int padding(
        DirectBuffer data,
        int index,
        int length)
    {
        return 0;
    }
}
