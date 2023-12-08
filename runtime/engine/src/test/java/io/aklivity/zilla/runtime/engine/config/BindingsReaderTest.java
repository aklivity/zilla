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
package io.aklivity.zilla.runtime.engine.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.internal.Bindings;
import io.aklivity.zilla.runtime.engine.reader.BindingsReader;

public class BindingsReaderTest
{
    @Test
    public void shouldReadBindings()
    {
        // GIVEN
        Path directory = Paths.get("target/zilla-itests");
        Bindings bindings = Bindings.builder().directory(directory).build();
        BindingConfig binding = new BindingConfig("vault", "test0", "test", KindConfig.SERVER, "entry",
            null, List.of(), null);
        bindings.writeBindingInfo(binding);
        BindingsReader bindingsReader = BindingsReader.builder().directory(directory).build();

        // WHEN
        Map<Long, long[]> result = bindingsReader.bindings();

        // THEN
        assertThat(result.size(), equalTo(1));
        assertThat(result.get(0L)[0], equalTo(0L));
        assertThat(result.get(0L)[1], equalTo(0L));
        assertThat(result.get(0L)[2], equalTo(0L));
        assertThat(result.get(0L)[3], equalTo(0L));
    }
}
