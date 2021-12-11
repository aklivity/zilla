/*
 * Copyright 2021-2021 Aklivity Inc.
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
package io.aklivity.zilla.engine.drive.internal.context;

import io.aklivity.zilla.engine.drive.cog.Axle;
import io.aklivity.zilla.engine.drive.cog.stream.StreamFactory;
import io.aklivity.zilla.engine.drive.config.Binding;

final class BindingContext
{
    private final Binding binding;
    private final Axle elektron;

    private StreamFactory attached;

    BindingContext(
        Binding binding,
        Axle elektron)
    {
        this.binding = binding;
        this.elektron = elektron;
    }

    public void attach()
    {
        attached = elektron.attach(binding);
    }

    public void detach()
    {
        elektron.detach(binding);
        attached = null;
    }

    public StreamFactory streamFactory()
    {
        return attached;
    }
}
