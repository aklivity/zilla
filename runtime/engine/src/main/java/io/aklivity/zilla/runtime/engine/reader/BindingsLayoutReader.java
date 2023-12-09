/*
 * Copyright 2021-2023 Aklivity Inc.
 *
 * Aklivity licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.aklivity.zilla.runtime.engine.reader;

import java.nio.file.Path;
import java.util.Map;

import io.aklivity.zilla.runtime.engine.internal.layouts.BindingsLayout;

public final class BindingsLayoutReader
{
    private final BindingsLayout layout;

    private BindingsLayoutReader(
        BindingsLayout layout)
    {
        this.layout = layout;
    }

    public Map<Long, long[]> bindings()
    {
        return layout.bindings();
    }

    public static Builder builder()
    {
        return new Builder();
    }

    public static final class Builder
    {
        private Path directory;

        public Builder directory(
            Path directory)
        {
            this.directory = directory;
            return this;
        }

        public BindingsLayoutReader build()
        {
            BindingsLayout bindingsLayout = BindingsLayout.builder()
                .directory(directory)
                .readonly(true)
                .build();
            return new BindingsLayoutReader(bindingsLayout);
        }
    }
}
