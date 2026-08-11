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

import io.aklivity.zilla.runtime.engine.model.ModelStatus;

/**
 * The consume end of a {@link KafkaTransform} chain. Each
 * {@link #transform(KafkaController, KafkaSource, KafkaEvent)} delivers one event, with {@code source}
 * positioned to read the content it carries, and reports a {@link ModelStatus}; {@code control} steers
 * the {@link KafkaPipeline} driving the chain.
 * <p>
 * The terminal sink is supplied by the pipeline's owner and writes each lane's content into its
 * destination region as the event arrives. The downstream of a {@link KafkaTransform} is also a
 * {@code KafkaSink}, so stages compose without knowing whether their downstream is another stage or the
 * terminal.
 * </p>
 * <p>
 * A lane switch selects the destination of the single {@link KafkaEvent#FIELD} that follows it. A field
 * arriving with no switch ahead of it is one the traversal merely surfaced on its way to its own
 * destination, and a terminal writes nothing for it — the pipeline's opening announcement of the lane it
 * is traversing reaches the stages, which need to know where the fields are coming from, but never the
 * terminal, which only ever writes what a stage appended.
 * </p>
 *
 * @see KafkaTransform
 */
@FunctionalInterface
public interface KafkaSink
{
    /**
     * Consumes one event.
     *
     * @param control  the control handle for the pipeline driving the chain
     * @param source   the read-only view of the content the event carries
     * @param event    the event
     * @return the outcome of consuming the event
     */
    ModelStatus transform(
        KafkaController control,
        KafkaSource source,
        KafkaEvent event);
}
