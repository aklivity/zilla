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
package io.aklivity.zilla.runtime.binding.kafka.internal.stream;

import io.aklivity.zilla.runtime.binding.kafka.internal.cache.KafkaCachePartition;

final class KafkaShareRecord
{
    static final int STATE_STAGED = 0;
    static final int STATE_READY = 1;
    static final int STATE_DELIVERING = 2;
    static final int STATE_DELIVERED = 3;

    int partitionId;
    long partitionOffset;
    long sequence;
    int bytes;
    KafkaCachePartition.Node node;
    int position;
    long timestamp;
    int deliveryCount;
    int redeliveries;
    long expiresAt;
    long deliverBy;
    long ownerId;
    int state;
}
