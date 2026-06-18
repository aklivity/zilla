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
package io.aklivity.zilla.runtime.common.yaml.internal;

/**
 * The kinds of event emitted by {@link YamlParser} over a YAML 1.2 stream, mirroring the YAML representation
 * graph: a stream wraps one or more documents, each document wraps a single root node, and nodes are mappings,
 * sequences, scalars or alias references. A mapping's children alternate key node, value node; the JSON layer
 * built on top of this stream is responsible for rejecting keys that JSON cannot represent.
 */
public enum YamlEventType
{
    STREAM_START,
    STREAM_END,
    DOCUMENT_START,
    DOCUMENT_END,
    MAPPING_START,
    MAPPING_END,
    SEQUENCE_START,
    SEQUENCE_END,
    SCALAR,
    ALIAS
}
