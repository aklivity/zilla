/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.runtime.binding.a2a.internal.card;

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;

public interface A2aCardConsumer
{
    void onCard(
        long traceId,
        long authorization,
        DirectBufferEx buffer,
        int offset,
        int limit,
        long maxAgeMillis);

    void onCardRejected(
        long traceId,
        long authorization,
        int status,
        String challenge);
}
