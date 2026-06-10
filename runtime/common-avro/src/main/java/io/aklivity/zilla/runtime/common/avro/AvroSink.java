/*
 * Copyright 2021-2024 Aklivity Inc
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
package io.aklivity.zilla.runtime.common.avro;

/**
 * The consume end of an {@link AvroDecodePipeline}. Each {@link #feed(AvroEvent, AvroSource)}
 * delivers one decoded event (with {@code in} positioned to read its scalar) and returns whether
 * the current top-level value has reached a terminal {@link AvroDecodePipeline.Status}. Third
 * parties may implement this contract to consume the decoded event stream — for example, the
 * {@code model-avro} {@code AvroJson} bridge drives a {@code common-json} generator from these
 * events.
 */
public interface AvroSink
{
    AvroDecodePipeline.Status feed(
        AvroEvent event,
        AvroSource in);

    default void reset()
    {
    }
}
