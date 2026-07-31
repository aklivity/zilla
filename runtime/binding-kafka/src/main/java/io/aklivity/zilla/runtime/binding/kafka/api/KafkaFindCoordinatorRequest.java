/*
 * Copyright 2021-2026 Aklivity Inc.
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
package io.aklivity.zilla.runtime.binding.kafka.api;

import io.aklivity.zilla.runtime.binding.kafka.internal.types.codec.find_coordinator.FindCoordinatorRequestFW;
import io.aklivity.zilla.runtime.common.agrona.buffer.MutableDirectBufferEx;
import io.aklivity.zilla.runtime.common.lang.Strings;

/**
 * FindCoordinator v3 always resolves a group coordinator ({@code keyType} 0), the only key type
 * a consumer-group caller needs.
 */
public final class KafkaFindCoordinatorRequest
{
    private static final short FIND_COORDINATOR_API_VERSION_V3 = 3;
    private static final byte KEY_TYPE_GROUP = 0;

    private KafkaFindCoordinatorRequest()
    {
    }

    /**
     * The exact number of bytes {@link Generator#generate(String)} will write for {@code groupId} at
     * {@code apiVersion}. Only version 3 is implemented today; a future version's formula would
     * branch alongside it here rather than replacing it.
     */
    public static int sizeof(
        String groupId,
        short apiVersion)
    {
        if (apiVersion != FIND_COORDINATOR_API_VERSION_V3)
        {
            throw new UnsupportedOperationException("unsupported FindCoordinator API version: " + apiVersion);
        }

        final int length = Strings.utf8Length(groupId);

        return varintWidth(length + 1) + length + 1 + 1;
    }

    private static int varintWidth(
        int value)
    {
        int width = 1;
        int remaining = value >>> 7;
        while (remaining != 0)
        {
            width++;
            remaining >>>= 7;
        }
        return width;
    }

    public static final class Generator
    {
        private final FindCoordinatorRequestFW.Builder findCoordinatorRequestRW = new FindCoordinatorRequestFW.Builder();

        private MutableDirectBufferEx buffer;
        private int limit;
        private int progress;

        public Generator wrap(
            MutableDirectBufferEx buffer,
            int offset,
            int limit)
        {
            this.buffer = buffer;
            this.limit = limit;
            this.progress = offset;
            return this;
        }

        /**
         * Writes the FindCoordinator v3 request body for {@code groupId}. Returns {@code false} if it
         * did not fit the buffer.
         */
        public boolean generate(
            String groupId)
        {
            boolean built;
            try
            {
                final FindCoordinatorRequestFW findCoordinatorRequest = findCoordinatorRequestRW
                    .wrap(buffer, progress, limit)
                    .key(groupId)
                    .keyType(KEY_TYPE_GROUP)
                    .taggedFields(0)
                    .build();

                progress = findCoordinatorRequest.limit();
                built = true;
            }
            catch (IndexOutOfBoundsException ex)
            {
                built = false;
            }
            return built;
        }

        public int limit()
        {
            return progress;
        }
    }
}
