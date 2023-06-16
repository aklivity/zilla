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
package io.aklivity.zilla.runtime.engine.metrics.reader;

import static io.aklivity.zilla.runtime.engine.metrics.Metric.Kind.COUNTER;
import static io.aklivity.zilla.runtime.engine.metrics.Metric.Kind.GAUGE;
import static io.aklivity.zilla.runtime.engine.metrics.Metric.Kind.HISTOGRAM;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.agrona.LangUtil;

import io.aklivity.zilla.runtime.engine.internal.LabelManager;
import io.aklivity.zilla.runtime.engine.internal.layouts.metrics.LayoutManager;
import io.aklivity.zilla.runtime.engine.internal.layouts.metrics.MetricsLayout;
import io.aklivity.zilla.runtime.engine.metrics.Metric;

public class MetricsReaderFactory
{
    private final Path enginePath;
    private final String namespaceName;
    private final String bindingName;

    public MetricsReaderFactory(
        Path enginePath,
        String namespaceName,
        String bindingName)
    {
        this.enginePath = enginePath;
        this.namespaceName = namespaceName;
        this.bindingName = bindingName;
    }

    public MetricsReader create()
    {
        try
        {
            LayoutManager layoutManager = new LayoutManager(enginePath);
            Map<Metric.Kind, List<MetricsLayout>> layouts = Map.of(
                COUNTER, layoutManager.countersLayouts(),
                GAUGE, layoutManager.gaugesLayouts(),
                HISTOGRAM, layoutManager.histogramsLayouts());
            LabelManager labels = new LabelManager(enginePath);
            return new MetricsReader(layouts, labels, namespaceName, bindingName);
        }
        catch (IOException ex)
        {
            LangUtil.rethrowUnchecked(ex);
            return null;
        }
    }
}
