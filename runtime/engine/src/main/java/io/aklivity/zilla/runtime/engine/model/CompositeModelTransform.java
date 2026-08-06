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
package io.aklivity.zilla.runtime.engine.model;

import java.util.List;

/**
 * A {@link ModelTransform} that chains an ordered list of stages entirely in the format-agnostic domain,
 * feeding each stage's answer to the next.
 * <p>
 * A format adapter is always handed exactly one {@code ModelTransform} — a leaf or a composite — so it
 * pays exactly one format-to-generic context switch per field however many policies that transform
 * represents.
 * </p>
 *
 * @see ModelTransform
 */
public final class CompositeModelTransform implements ModelTransform
{
    private final ModelTransform[] transforms;
    private final ModelSink[] stages;
    private final boolean identity;

    private ModelSink terminal;

    /**
     * Composes the given stages, in data-flow order, into a single {@link ModelTransform}. An empty list
     * yields {@link ModelTransform#NONE} and a single stage yields that stage, so no composite is built
     * where none is needed.
     *
     * @param transforms  the stages to compose, in data-flow order
     * @return the composed transform
     */
    public static ModelTransform of(
        List<ModelTransform> transforms)
    {
        ModelTransform composed;
        if (transforms == null || transforms.isEmpty())
        {
            composed = ModelTransform.NONE;
        }
        else if (transforms.size() == 1)
        {
            composed = transforms.get(0);
        }
        else
        {
            composed = new CompositeModelTransform(transforms);
        }
        return composed;
    }

    private CompositeModelTransform(
        List<ModelTransform> transforms)
    {
        this.transforms = transforms.toArray(ModelTransform[]::new);
        this.stages = new ModelSink[this.transforms.length];
        for (int i = 0; i < this.stages.length; i++)
        {
            this.stages[i] = new Stage(i + 1);
        }
        this.identity = transforms.stream().allMatch(ModelTransform::identity);
    }

    @Override
    public FieldStatus transform(
        ModelController control,
        ModelSource source,
        FieldEvent event,
        ModelSink sink)
    {
        this.terminal = sink;
        return transforms[0].transform(control, source, event, stages[0]);
    }

    @Override
    public FieldStatus resume(
        ModelController control,
        ModelSource source,
        FieldEvent event,
        ModelSink sink)
    {
        this.terminal = sink;
        return transforms[0].resume(control, source, event, stages[0]);
    }

    @Override
    public FieldStatus flush(
        ModelController control,
        ModelSource source,
        ModelSink sink)
    {
        this.terminal = sink;
        return transforms[0].flush(control, source, stages[0]);
    }

    @Override
    public void reset()
    {
        for (ModelTransform transform : transforms)
        {
            transform.reset();
        }
    }

    @Override
    public boolean identity()
    {
        return identity;
    }

    // the downstream handed to transforms[index - 1]: it invokes the next stage, or the terminal sink the
    // adapter supplied for the current call once the chain is exhausted
    private final class Stage implements ModelSink
    {
        private final int index;

        private Stage(
            int index)
        {
            this.index = index;
        }

        @Override
        public FieldStatus transform(
            ModelController control,
            ModelSource source,
            FieldEvent event)
        {
            return index < transforms.length
                ? transforms[index].transform(control, source, event, stages[index])
                : terminal.transform(control, source, event);
        }

        @Override
        public FieldStatus resume(
            ModelController control,
            ModelSource source,
            FieldEvent event)
        {
            return index < transforms.length
                ? transforms[index].resume(control, source, event, stages[index])
                : terminal.resume(control, source, event);
        }

        @Override
        public FieldStatus flush(
            ModelController control,
            ModelSource source)
        {
            return index < transforms.length
                ? transforms[index].flush(control, source, stages[index])
                : terminal.flush(control, source);
        }

        @Override
        public boolean identity()
        {
            boolean downstream = terminal == null || terminal.identity();
            for (int i = index; downstream && i < transforms.length; i++)
            {
                downstream = transforms[i].identity();
            }
            return downstream;
        }
    }
}
