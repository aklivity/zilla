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
package io.aklivity.zilla.runtime.filesystem.http;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.net.URI;
import java.nio.file.Path;

import org.junit.Test;

public class TestHttpFileSystem
{
    @Test
    public void testHttpPath() throws Exception
    {
        // GIVEN
        String httpUrl = "http://localhost:4242/hello.txt";

        // WHEN
        Path path = Path.of(new URI(httpUrl));

        // THEN
        assertThat(path.getFileSystem().getClass().getSimpleName(), equalTo("HttpFileSystem"));
        assertThat(path.getFileSystem().provider().getClass().getSimpleName(), equalTo("HttpFileSystemProvider"));
        assertThat(path.getFileSystem().provider().getScheme(), equalTo("http"));
    }

    @Test
    public void testHttpsPath() throws Exception
    {
        // GIVEN
        String httpsUrl = "https://localhost:4242/hello.txt";

        // WHEN
        Path path = Path.of(new URI(httpsUrl));

        // THEN
        assertThat(path.getFileSystem().getClass().getSimpleName(), equalTo("HttpFileSystem"));
        assertThat(path.getFileSystem().provider().getClass().getSimpleName(), equalTo("HttpsFileSystemProvider"));
        assertThat(path.getFileSystem().provider().getScheme(), equalTo("https"));
    }

    @Test
    public void testHttpSiblingString() throws Exception
    {
        // GIVEN
        String httpUrl = "http://localhost:4242/greeting/hello.txt";
        Path path = Path.of(new URI(httpUrl));

        // WHEN
        Path sibling = path.resolveSibling("bye.txt");

        // THEN
        assertThat(sibling.getFileSystem().getClass().getSimpleName(), equalTo("HttpFileSystem"));
        assertThat(sibling.getFileSystem().provider().getClass().getSimpleName(), equalTo("HttpFileSystemProvider"));
        assertThat(sibling.toString(), equalTo("http://localhost:4242/greeting/bye.txt"));
    }


    @Test
    public void testHttpsSiblingString() throws Exception
    {
        // GIVEN
        String httpUrl = "https://localhost:4242/greeting/hello.txt";
        Path path = Path.of(new URI(httpUrl));

        // WHEN
        Path sibling = path.resolveSibling("bye.txt");

        // THEN
        assertThat(sibling.getFileSystem().getClass().getSimpleName(), equalTo("HttpFileSystem"));
        assertThat(sibling.getFileSystem().provider().getClass().getSimpleName(), equalTo("HttpsFileSystemProvider"));
        assertThat(sibling.toString(), equalTo("https://localhost:4242/greeting/bye.txt"));
    }
}
