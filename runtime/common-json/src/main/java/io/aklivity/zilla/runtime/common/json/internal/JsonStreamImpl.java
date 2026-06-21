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
package io.aklivity.zilla.runtime.common.json.internal;

import java.util.ArrayList;
import java.util.List;

import io.aklivity.zilla.runtime.common.json.JsonController;
import io.aklivity.zilla.runtime.common.json.JsonEvent;
import io.aklivity.zilla.runtime.common.json.JsonGeneratorEx;
import io.aklivity.zilla.runtime.common.json.JsonParserEx;
import io.aklivity.zilla.runtime.common.json.JsonPipeline;
import io.aklivity.zilla.runtime.common.json.JsonPipeline.Status;
import io.aklivity.zilla.runtime.common.json.JsonReporter;
import io.aklivity.zilla.runtime.common.json.JsonSink;
import io.aklivity.zilla.runtime.common.json.JsonSource;
import io.aklivity.zilla.runtime.common.json.JsonStream;
import io.aklivity.zilla.runtime.common.json.JsonTransform;

/**
 * Backs {@link JsonStream}: holds the {@link JsonParserEx} driver plus the ordered list of
 * {@link JsonTransform} stages appended via {@link #transform(JsonTransform)}. {@link #into(JsonSink)}
 * binds the chain back-to-front into a single root {@link JsonSink} and returns a runnable
 * {@link JsonPipelineImpl}.
 */
public final class JsonStreamImpl implements JsonStream
{
    private final JsonParserEx parser;
    private final List<JsonTransform> transforms;

    private JsonReporter reporter;

    public JsonStreamImpl(
        JsonParserEx parser)
    {
        this.parser = parser;
        this.transforms = new ArrayList<>();
    }

    @Override
    public JsonStream transform(
        JsonTransform transform)
    {
        transforms.add(transform);
        return this;
    }

    @Override
    public JsonStream reporting(
        JsonReporter reporter)
    {
        this.reporter = reporter;
        return this;
    }

    @Override
    public JsonPipeline into(
        JsonSink sink)
    {
        return new JsonPipelineImpl(parser, bind(sink), reporter, null);
    }

    @Override
    public JsonPipeline into(
        JsonGeneratorEx generator)
    {
        // the generator self-wraps an empty buffer on construction, so it is already in a sink-safe
        // state here; transform re-targets it at the caller's destination per call
        return new JsonPipelineImpl(parser, bind(new JsonSinkImpl(generator)), reporter, generator);
    }

    private JsonSink bind(
        JsonSink sink)
    {
        JsonSink root = sink;
        for (int i = transforms.size() - 1; i >= 0; i--)
        {
            root = new BoundSink(transforms.get(i), root);
        }
        return root;
    }

    private static final class BoundSink implements JsonSink
    {
        private final JsonTransform transform;
        private final JsonSink downstream;

        private BoundSink(
            JsonTransform transform,
            JsonSink downstream)
        {
            this.transform = transform;
            this.downstream = downstream;
        }

        @Override
        public Status feed(
            JsonController control,
            JsonSource source,
            JsonEvent event)
        {
            return transform.feed(control, source, event, downstream);
        }

        @Override
        public Status resume(
            JsonController control,
            JsonSource source,
            JsonEvent event)
        {
            return transform.resume(control, source, event, downstream);
        }

        @Override
        public Status flush(
            JsonController control,
            JsonSource source)
        {
            return transform.flush(control, source, downstream);
        }

        @Override
        public void reset()
        {
            transform.reset();
            downstream.reset();
        }
    }
}
