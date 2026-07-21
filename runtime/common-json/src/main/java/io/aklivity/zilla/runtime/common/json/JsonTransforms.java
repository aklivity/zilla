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
package io.aklivity.zilla.runtime.common.json;

import java.util.List;
import java.util.Map;

import io.aklivity.zilla.runtime.common.json.internal.JsonFlattenerImpl;
import io.aklivity.zilla.runtime.common.json.internal.JsonProjectorImpl;

/**
 * Library of general-purpose, reusable {@link JsonTransform} implementations, distinct from {@link JsonEx}
 * (which assembles pipeline plumbing — parsers, generators, streams, sinks). Add a stage from here to a
 * pipeline via {@link JsonStream#transform(JsonTransform)}.
 */
public final class JsonTransforms
{
    private JsonTransforms()
    {
    }

    /**
     * Returns a {@link JsonTransform} that prunes a document to the given retained RFC 6901
     * pointers, forwarding each kept event to the downstream sink supplied at assembly. Reuse a single
     * instance per thread; it resets per top-level value.
     */
    public static JsonTransform projector(
        List<String> pointers)
    {
        return new JsonProjectorImpl(pointers);
    }

    /**
     * Returns a {@link JsonTransform} pruning a document to the paths retained by {@code schema}
     * (see {@link JsonSchema#retainedPaths()}).
     */
    public static JsonTransform projector(
        JsonSchema schema)
    {
        return new JsonProjectorImpl(schema.retainedPaths());
    }

    /**
     * Returns a {@link JsonTransform} that hoists each of {@code accessorTargets}' dotted key paths (e.g.
     * {@code "pr.title"}) up to its target top-level key (e.g. {@code "title"}), discarding every ancestor
     * wrapper container along the way. Chain it downstream of {@link #projector(List)} retaining the same
     * accessors' RFC 6901 pointers, so only the targeted paths ever reach this stage. Reuse a single
     * instance per thread; it resets per top-level value.
     */
    public static JsonTransform flatten(
        Map<String, String> accessorTargets)
    {
        return new JsonFlattenerImpl(accessorTargets);
    }
}
