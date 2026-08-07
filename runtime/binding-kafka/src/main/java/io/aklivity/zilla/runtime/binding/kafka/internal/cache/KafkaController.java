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
package io.aklivity.zilla.runtime.binding.kafka.internal.cache;

/**
 * The per-edge control handle a {@link KafkaTransform} stage uses to steer the {@link KafkaPipeline}
 * driving it.
 * <p>
 * A mediating stage supplies its own {@code KafkaController} to its downstream; a non-mediating stage
 * passes {@code control} through, so a signal raised by any stage reaches the pipeline.
 * </p>
 *
 * @see KafkaTransform
 */
interface KafkaController
{
    /**
     * Signals that the message in flight must be rejected, supplying the diagnostic the pipeline reports.
     * <p>
     * A stage raising this also returns {@link io.aklivity.zilla.runtime.engine.model.ModelStatus#REJECTED};
     * the diagnostic is what distinguishes a rejection with a cause from a bare status.
     * </p>
     *
     * @param diagnostic  the reason the message was rejected
     */
    void reject(
        String diagnostic);
}
