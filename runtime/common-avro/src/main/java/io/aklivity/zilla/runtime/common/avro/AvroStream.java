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
 * A description of a {@code common-avro} pipeline — the schema-bound parse driver (from
 * {@link AvroSchema#parser()}) plus an ordered list of {@link AvroTransform} stages. Append stages with
 * {@link #transform(AvroTransform)} (left-to-right, in data-flow order); terminate with
 * {@link #into(AvroSink)} to obtain the runnable, resumable {@link AvroPipeline}. An {@code AvroStream}
 * carries no state and is not itself runnable.
 */
public interface AvroStream
{
    AvroStream transform(
        AvroTransform transform);

    AvroPipeline into(
        AvroSink sink);
}
