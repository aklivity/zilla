/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.metrics.grpc.internal;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.Configuration;
import io.aklivity.zilla.runtime.engine.metrics.MetricGroup;
import io.aklivity.zilla.runtime.engine.metrics.MetricGroupFactory;

public final class GrpcMetricGroupFactoryTest
{
    @Test
    public void shouldLoadAndCreate()
    {
        Configuration config = new Configuration();
        MetricGroupFactory factory = MetricGroupFactory.instantiate();
        MetricGroup metricGroup = factory.create("grpc", config);

        assertThat(metricGroup, instanceOf(GrpcMetricGroup.class));
        assertThat(metricGroup.name(), equalTo("grpc"));
    }
}
