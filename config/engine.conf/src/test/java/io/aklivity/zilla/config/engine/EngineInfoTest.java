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
package io.aklivity.zilla.config.engine;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;

import java.net.URL;
import java.util.Collection;

import org.junit.Test;

import io.aklivity.zilla.config.engine.test.internal.model.TestModelExtInfo;

public class EngineInfoTest
{
    @Test
    public void shouldIncludeModelExtensionSchemaPatches()
    {
        EngineInfo info = new EngineInfo();

        Collection<URL> patches = info.patches();

        assertThat(patches, hasItem(new TestModelExtInfo().schema()));
    }
}
