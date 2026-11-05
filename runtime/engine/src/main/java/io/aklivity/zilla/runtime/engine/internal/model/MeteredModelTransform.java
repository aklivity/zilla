/*
 * Copyright 2021-2026 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.internal.model;

import io.aklivity.zilla.runtime.engine.internal.model.ModelMetrics.Recorders;
import io.aklivity.zilla.runtime.engine.model.ModelController;
import io.aklivity.zilla.runtime.engine.model.ModelEvent;
import io.aklivity.zilla.runtime.engine.model.ModelSink;
import io.aklivity.zilla.runtime.engine.model.ModelSource;
import io.aklivity.zilla.runtime.engine.model.ModelStatus;
import io.aklivity.zilla.runtime.engine.model.ModelTransform;

final class MeteredModelTransform implements ModelTransform
{
    private final ModelTransform transform;
    private final ModelSink bridge;

    private Recorders recorders;
    private ModelSink terminal;

    MeteredModelTransform(
        ModelTransform transform)
    {
        this.transform = transform;
        this.bridge = new MeteredModelSink();
    }

    void recorders(
        Recorders recorders)
    {
        this.recorders = recorders;
    }

    @Override
    public ModelStatus transform(
        ModelController control,
        ModelSource source,
        ModelEvent event,
        ModelSink sink)
    {
        this.terminal = sink;
        return transform.transform(control, source, event, bridge);
    }

    @Override
    public ModelStatus resume(
        ModelController control,
        ModelSource source,
        ModelEvent event,
        ModelSink sink)
    {
        this.terminal = sink;
        return transform.resume(control, source, event, bridge);
    }

    @Override
    public ModelStatus flush(
        ModelController control,
        ModelSource source,
        ModelSink sink)
    {
        this.terminal = sink;
        return transform.flush(control, source, bridge);
    }

    @Override
    public void reset()
    {
        transform.reset();
    }

    @Override
    public boolean identity()
    {
        return transform.identity();
    }

    @Override
    public boolean deterministic()
    {
        return transform.deterministic();
    }

    private void record(
        ModelController control,
        ModelEvent event)
    {
        if (recorders != null)
        {
            if (event == ModelEvent.REPLACED)
            {
                recorders.replaced.record(control.authorization(), 1L);
            }
            else if (event == ModelEvent.DECLINED)
            {
                recorders.declined.record(control.authorization(), 1L);
            }
        }
    }

    private final class MeteredModelSink implements ModelSink
    {
        @Override
        public ModelStatus transform(
            ModelController control,
            ModelSource source,
            ModelEvent event)
        {
            record(control, event);
            return terminal.transform(control, source, event);
        }

        @Override
        public ModelStatus resume(
            ModelController control,
            ModelSource source,
            ModelEvent event)
        {
            record(control, event);
            return terminal.resume(control, source, event);
        }

        @Override
        public ModelStatus flush(
            ModelController control,
            ModelSource source)
        {
            return terminal.flush(control, source);
        }

        @Override
        public void reset()
        {
            terminal.reset();
        }

        @Override
        public boolean identity()
        {
            return terminal.identity();
        }
    }
}
