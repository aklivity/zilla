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
package io.aklivity.zilla.runtime.binding.filesystem.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class FileSystemDigestTest
{
    @Test
    public void shouldDigestAcrossMultipleReads() throws Exception
    {
        FileSystemDigest digest = new FileSystemDigest(new byte[8]);

        String hash = digest.digest(input("a".repeat(16383) + "b"));

        assertThat(hash, equalTo("830cc3416ed05824855e80b49e242446"));
    }

    @Test
    public void shouldDigestChangeBeyondFirstRead() throws Exception
    {
        FileSystemDigest digest = new FileSystemDigest(new byte[8]);

        String before = digest.digest(input("a".repeat(16384)));
        String after = digest.digest(input("a".repeat(16383) + "b"));

        assertThat(after, not(equalTo(before)));
    }

    private static ByteArrayInputStream input(
        String text)
    {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }
}
