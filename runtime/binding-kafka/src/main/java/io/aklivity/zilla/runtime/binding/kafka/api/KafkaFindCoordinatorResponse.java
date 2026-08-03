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

import io.aklivity.zilla.runtime.common.agrona.buffer.DirectBufferEx;

/**
 * A decoded FindCoordinator response - a single result, unlike the array-shaped responses
 * elsewhere in this package, so there is no cursor to drive.
 */
public interface KafkaFindCoordinatorResponse
{
    DirectBufferEx buffer();

    int throttleTimeMillis();

    short error();

    /**
     * @return -1 if no error message is present
     */
    int messageOffset();

    int messageLength();

    int nodeId();

    int hostOffset();

    int hostLength();

    int port();
}
