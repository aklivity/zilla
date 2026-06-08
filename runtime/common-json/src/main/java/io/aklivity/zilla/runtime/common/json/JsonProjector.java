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
package io.aklivity.zilla.runtime.common.json;

import jakarta.json.stream.JsonParser;

import org.agrona.MutableDirectBuffer;

/**
 * A resumable, event-driven {@link JsonEventConsumer} that projects a JSON document down to a set
 * of retained paths and forwards the kept events to a downstream sink.
 * <p>
 * Retained paths are RFC 6901 JSON Pointers; a {@code -} array-index segment is a wildcard
 * matching any index. A node is forwarded when it lies on a retained branch — either a retained
 * pointer is a prefix of the node's path (the node is inside a retained subtree, kept whole) or
 * the node's path is a prefix of a retained pointer (an ancestor, descended to reach retained
 * descendants). Every other subtree is dropped. Output is in source order.
 * <p>
 * As a {@link JsonEventConsumer} the projector is fed one parser event at a time via {@link
 * #feed(jakarta.json.stream.JsonParser.Event, JsonParser)} and holds no reference to the parser
 * between feeds, so it can be paused at any event boundary while the upstream slot-fragmented
 * parser awaits more bytes. Obtain an instance via {@link StreamingJson#createProjector(
 * java.util.List)} (which projects into an internal generator) or {@link
 * StreamingJson#createProjector(java.util.List, JsonEventConsumer)} (which forwards kept events to
 * an explicit downstream sink).
 */
public interface JsonProjector extends JsonEventConsumer
{
    /**
     * Drives the parser to project the next top-level value into {@code buffer} starting at
     * {@code offset} and returns the number of bytes written. Only available when the projector
     * was created without an explicit sink (the internal generator is used); throws {@link
     * IllegalStateException} otherwise.
     */
    int project(
        JsonParser parser,
        MutableDirectBuffer buffer,
        int offset);
}
