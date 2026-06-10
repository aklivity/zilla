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
 * A runnable Avro encode pipeline: an {@link AvroEvent} stream in, Avro binary out to the target
 * buffer the pipeline was created over, validating each event against the compiled
 * {@link AvroSchema}. Reuse a single instance per worker thread: call {@link #reset()} once per
 * top-level value, {@link #feed(AvroEvent, AvroSource)} per event, then {@link #length()} for the
 * number of bytes written. Zero per-message allocation; abort on a schema violation.
 */
public interface AvroEncodePipeline
{
    void reset();

    void feed(
        AvroEvent event,
        AvroSource in);

    /**
     * @return the number of Avro binary bytes written to the target buffer for the current value
     */
    int length();
}
