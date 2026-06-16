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
 * A compiled, immutable Avro schema model. Compiling parses the Avro schema document once (off the hot
 * path) into a structure that drives streaming parse and generate; callers cache one instance per schema,
 * keyed by their own schema identifier. Obtain a parser or generator from {@link Avro#parser(AvroSchema)}
 * and {@link Avro#generator(AvroSchema, org.agrona.MutableDirectBuffer, int)}. The compiled schema is
 * immutable and may back many pipelines, but the pipelines it produces are not thread-safe.
 */
public interface AvroSchema
{
    /**
     * A streaming validator stage that forwards the parsed event stream while the driver validates
     * against this schema as it reads (emit-then-abort). Compose it before a sink to validate-then-convert,
     * or before a discarding sink to validate only.
     */
    AvroTransform validator();

    /**
     * The root {@link AvroType} of this schema, for inspecting its structure (fields, branches, symbols,
     * logical types) off the hot path. Navigable by reference, so a recursive schema is a cyclic graph.
     */
    AvroType type();
}
