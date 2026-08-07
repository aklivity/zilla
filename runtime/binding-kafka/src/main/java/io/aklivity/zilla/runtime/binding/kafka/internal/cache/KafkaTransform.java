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
 * A stage in a {@link KafkaPipeline}, observing the whole message — key, headers, and value — as one
 * event stream and appending to any lane at the moment it has something to append.
 * <p>
 * Each {@link #transform(KafkaController, KafkaSource, KafkaEvent, KafkaSink)} consumes one event and
 * forwards what it keeps to {@code sink}, the downstream supplied by the caller. Because the three lanes
 * are independently writable, a stage that discovers content mid-traversal emits
 * {@link KafkaEvent#SWITCH_KEY} or {@link KafkaEvent#SWITCH_HEADERS}, the {@link KafkaEvent#FIELD} it
 * found, and then the switch back to the lane it was reading — all before forwarding the event it was
 * given. Nothing is held aside for a later replay, so a stage needs no buffer of its own.
 * </p>
 * <p>
 * A stage never sees {@link ModelStatus#OVERFLOW}: the terminal writes each lane straight into its
 * destination region rather than into a bounded output, so there is nothing to drain and no suspend to
 * resume from.
 * </p>
 *
 * @see KafkaEvent
 * @see KafkaPipeline
 */
interface KafkaTransform
{
    /**
     * Identity stage that forwards every event unchanged.
     */
    KafkaTransform NONE = new KafkaTransform()
    {
        @Override
        public ModelStatus transform(
            KafkaController control,
            KafkaSource source,
            KafkaEvent event,
            KafkaSink sink)
        {
            return sink.transform(control, source, event);
        }

        @Override
        public KafkaTransform andThen(
            KafkaTransform next)
        {
            return next;
        }
    };

    /**
     * Consumes one event and forwards what it keeps to {@code sink}.
     *
     * @param control  the control handle for the pipeline driving this chain
     * @param source   the read-only view of the content the event carries
     * @param event    the event
     * @param sink     the downstream stage
     * @return the outcome of consuming the event
     */
    ModelStatus transform(
        KafkaController control,
        KafkaSource source,
        KafkaEvent event,
        KafkaSink sink);

    /**
     * Returns a transform that feeds this stage's answer to {@code next}, so any number of stages compose
     * into the single {@code KafkaTransform} a {@link KafkaPipeline} drives.
     * <p>
     * Composing with {@link #NONE} yields the other stage rather than a wrapper: this returns
     * {@code this} for a {@code NONE} argument, and {@code NONE} overrides it to return {@code next}.
     * </p>
     *
     * @param next  the stage to feed this stage's answer to
     * @return the composed transform
     */
    default KafkaTransform andThen(
        KafkaTransform next)
    {
        KafkaTransform previous = this;
        return next == NONE ? previous : new KafkaTransform()
        {
            private KafkaSink terminal;

            // the downstream handed to previous: it invokes next with whatever terminal the current call
            // supplied, so the chain re-binds per call without either stage holding the caller's sink
            private final KafkaSink bridge = (control, source, event) -> next.transform(control, source, event, terminal);

            @Override
            public ModelStatus transform(
                KafkaController control,
                KafkaSource source,
                KafkaEvent event,
                KafkaSink sink)
            {
                this.terminal = sink;
                return previous.transform(control, source, event, bridge);
            }

            @Override
            public void reset()
            {
                previous.reset();
                next.reset();
            }
        };
    }

    /**
     * Discards any in-flight state so this stage is ready for the next message.
     */
    default void reset()
    {
    }
}
