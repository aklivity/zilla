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
 * The event currency of a {@link KafkaTransform} chain: the whole-message view of a Kafka record as its
 * key, headers, and value flow through a {@link KafkaPipeline}.
 * <p>
 * The vocabulary is a lane selector plus one shared content event, not three parallel vocabularies.
 * {@link #SWITCH_KEY}, {@link #SWITCH_HEADERS}, and {@link #SWITCH_VALUE} are repeatable context
 * switches rather than a bracket that is entered and left once: a stage that discovers key or header
 * content part-way through the value switches lane, appends what it found, and switches back, so the
 * value traversal resumes where it left off. {@link #FIELD} then carries content in whichever lane is
 * currently selected, so the same event serves a structured key, a structured value, and a header
 * without being duplicated per lane.
 * </p>
 * <p>
 * The headers lane reuses {@link #FIELD} with two differences rather than a separate shape: its values
 * are always raw binary, matching Kafka's {@code byte[]} header values, and its names may repeat,
 * matching Kafka's ordered list of {@code (String, byte[])} pairs. {@link #SWITCH_HEADERS} therefore
 * stays parameterless like the other two — the header's name arrives as the {@link #FIELD} that follows,
 * readable from {@link KafkaSource#getPath()}.
 * </p>
 *
 * @see KafkaTransform
 * @see KafkaPipeline
 */
enum KafkaEvent
{
    /** selects the key lane; {@link #FIELD} events that follow carry key content */
    SWITCH_KEY,
    /** selects the headers lane; {@link #FIELD} events that follow carry one header each */
    SWITCH_HEADERS,
    /** selects the value lane; {@link #FIELD} events that follow carry value content */
    SWITCH_VALUE,
    /** content in the currently selected lane, its name and value readable from the {@link KafkaSource} */
    FIELD
}
