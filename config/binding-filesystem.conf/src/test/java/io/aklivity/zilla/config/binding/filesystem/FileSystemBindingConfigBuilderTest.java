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
package io.aklivity.zilla.config.binding.filesystem;

import static io.aklivity.zilla.config.engine.KindConfig.SERVER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.net.URI;

import org.junit.Test;

public class FileSystemBindingConfigBuilderTest
{
    @Test
    public void shouldBuildBindingViaFluentKindSpecificChain()
    {
        FileSystemBindingConfig binding = FileSystemBindingConfig.builder()
            .namespace("test")
            .name("filesystem0")
            .kind(SERVER)
            .options()
                .location(URI.create("target/files"))
                .symlinks(FileSystemSymbolicLinksConfig.FOLLOW)
                .build()
            .build();

        assertThat(binding.namespace, equalTo("test"));
        assertThat(binding.name, equalTo("filesystem0"));
        assertThat(binding.type, equalTo(FileSystemBindingInfo.TYPE));
        assertThat(binding.kind, equalTo(SERVER));

        FileSystemOptionsConfig options = (FileSystemOptionsConfig) binding.options;
        assertThat(options.location, equalTo(URI.create("target/files")));
        assertThat(options.symlinks, equalTo(FileSystemSymbolicLinksConfig.FOLLOW));
    }
}
