/*
 * Copyright 2021-2026 Aklivity Inc
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
 * Something an {@link AvroTransform} stage can be appended to, in data-flow order. {@link AvroStream} is
 * the primary implementation; this narrower, self-bounded supertype lets a caller outside {@code common-avro}
 * append stages to an in-progress stream and get back its own concrete stream type, without depending on
 * {@link AvroStream}'s fuller, terminal-bearing API.
 *
 * @param <T>  the concrete stream type, so {@link #transform(AvroTransform)} returns it rather than this
 *             narrower supertype
 */
public interface AvroTransformable<T extends AvroTransformable<T>>
{
    T transform(
        AvroTransform transform);
}
