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
package io.aklivity.zilla.runtime.binding.llm.internal;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.Base64;

/**
 * Resolves a base64-encoded {@code data:} URL ({@code RFC 2397}) in-memory, with no temporary file and no
 * globally-registered protocol handler -- scoped to whichever {@link URL} it is passed to via the
 * {@code new URL(URL, String, URLStreamHandler)} constructor.
 */
final class LlmDataUrlStreamHandler extends URLStreamHandler
{
    private static final String BASE64_SUFFIX = ";base64";

    @Override
    protected URLConnection openConnection(
        URL url)
    {
        return new LlmDataUrlConnection(url);
    }

    private static final class LlmDataUrlConnection extends URLConnection
    {
        private LlmDataUrlConnection(
            URL url)
        {
            super(url);
        }

        @Override
        public void connect()
        {
            connected = true;
        }

        @Override
        public InputStream getInputStream() throws IOException
        {
            String spec = getURL().getFile();
            int comma = spec.indexOf(',');
            if (comma < 0 || !spec.substring(0, comma).endsWith(BASE64_SUFFIX))
            {
                throw new IOException("Malformed base64 data URL");
            }

            return new ByteArrayInputStream(Base64.getDecoder().decode(spec.substring(comma + 1)));
        }
    }
}
