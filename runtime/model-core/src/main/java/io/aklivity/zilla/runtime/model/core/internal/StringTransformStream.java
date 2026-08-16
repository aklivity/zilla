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
package io.aklivity.zilla.runtime.model.core.internal;

import java.util.ArrayList;
import java.util.List;

import io.aklivity.zilla.runtime.model.core.ext.StringTransform;
import io.aklivity.zilla.runtime.model.core.ext.StringTransformable;

// Collects the stages installed extensions contribute for one direction, in discovery order, for
// StringExtModelPipeline to bind sink-to-sink. A stage holds the in-flight state of one value, so a fresh
// stream is folded per pipeline rather than once per model configuration.
final class StringTransformStream implements StringTransformable<StringTransformStream>
{
    private final List<StringTransform> transforms;

    StringTransformStream()
    {
        this.transforms = new ArrayList<>();
    }

    @Override
    public StringTransformStream transform(
        StringTransform transform)
    {
        // NONE forwards every event verbatim, so dropping it here costs the assembled chain nothing per
        // event and lets an extension pass it unconditionally rather than branching
        if (transform != StringTransform.NONE)
        {
            transforms.add(transform);
        }
        return this;
    }

    List<StringTransform> transforms()
    {
        return transforms;
    }
}
