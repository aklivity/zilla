/*
 * Copyright 2021-2024 Aklivity Inc.
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
package io.aklivity.zilla.runtime.engine.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.aklivity.zilla.runtime.engine.namespace.NamespacedId;

public class LabelManagerTest
{
    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void shouldRoundTripLabel() throws Exception
    {
        LabelManager labels = new LabelManager(folder.getRoot().toPath());

        int labelId = labels.supplyLabelId("test");

        assertThat(labels.lookupLabel(labelId), equalTo("test"));
    }

    @Test
    public void shouldNotThrowForNoNamespaceIdSentinel() throws Exception
    {
        LabelManager labels = new LabelManager(folder.getRoot().toPath());

        String label = labels.lookupLabel(NamespacedId.NO_NAMESPACE_ID);

        assertThat(label, not(equalTo("test")));
    }

    @Test
    public void shouldNotThrowForZeroLabelId() throws Exception
    {
        LabelManager labels = new LabelManager(folder.getRoot().toPath());
        labels.supplyLabelId("test");

        assertThat(labels.lookupLabel(0), not(equalTo("test")));
    }

    @Test
    public void shouldNotThrowForUnregisteredLabelId() throws Exception
    {
        LabelManager labels = new LabelManager(folder.getRoot().toPath());
        labels.supplyLabelId("test");

        assertThat(labels.lookupLabel(999), not(equalTo("test")));
    }
}
