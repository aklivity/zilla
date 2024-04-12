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

import static io.aklivity.zilla.runtime.engine.config.KindConfig.SERVER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import org.junit.Test;

import io.aklivity.zilla.runtime.engine.config.BindingConfig;
import io.aklivity.zilla.runtime.engine.internal.layouts.BindingsLayout;

public class BindingsLayoutReaderTest
{
    @Test
    public void shouldReadBindings()
    {
        // GIVEN
        Path directory = Paths.get("target/zilla-itests");
        BindingsLayout layout = BindingsLayout.builder()
            .path(directory.resolve("bindings0"))
            .build();
        BindingConfig binding = BindingConfig.builder()
            .namespace("test")
            .name("test0")
            .type("test")
            .kind(SERVER)
            .build();
        binding.id = 1L;
        binding.typeId = 2L;
        binding.kindId = 3L;
        binding.originTypeId = 4L;
        binding.routedTypeId = 5L;
        layout.writeBindingInfo(binding);

        BindingsLayoutReader reader = BindingsLayoutReader.builder()
            .path(directory.resolve("bindings0"))
            .build();

        // WHEN
        Map<Long, long[]> result = reader.bindings();

        // THEN
        assertThat(result.size(), equalTo(1));
        assertThat(result.get(1L)[0], equalTo(2L));
        assertThat(result.get(1L)[1], equalTo(3L));
        assertThat(result.get(1L)[2], equalTo(4L));
        assertThat(result.get(1L)[3], equalTo(5L));
    }
}
