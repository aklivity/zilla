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
package io.aklivity.zilla.runtime.engine.test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.enumeration;
import static java.util.Collections.singleton;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.Enumeration;

public final class Services
{
    public static <T> ClassLoader newLoader(
        Class<T> service,
        Class<? extends T> implementation)
    {
        final String servicePath = String.format("META-INF/services/%s", service.getName());
        final URL serviceURL = newURL(service, implementation);
        return new URLClassLoader(new URL[] { serviceURL })
        {
            @Override
            public URL findResource(
                String name)
            {
                if (servicePath.equals(name))
                {
                    return serviceURL;
                }
                return super.findResource(name);
            }

            @Override
            public Enumeration<URL> findResources(
                String name) throws IOException
            {
                if (servicePath.equals(name))
                {
                    return enumeration(singleton(serviceURL));
                }

                return super.findResources(name);
            }
        };
    }

    private static <T> URL newURL(
        Class<T> service,
        Class<? extends T> implementation)
    {
        try
        {
            return new URL(null, String.format("data:,%s", implementation.getName()), new DataHandler());
        }
        catch (MalformedURLException ex)
        {
            throw new IllegalArgumentException(ex);
        }
    }

    private Services()
    {
        // utility class
    }

    private static final class DataHandler extends URLStreamHandler
    {
        @Override
        protected URLConnection openConnection(
            URL location) throws IOException
        {
            return new DataConnection(location);
        }

        private final class DataConnection extends URLConnection
        {
            private final byte[] contents;

            private DataConnection(
                URL location)
            {
                super(location);
                this.contents = location.getPath().substring(1).getBytes(UTF_8);
            }

            @Override
            public void connect() throws IOException
            {
                // no-op
            }

            @Override
            public InputStream getInputStream() throws IOException
            {
                return new ByteArrayInputStream(contents);
            }
        }
    }
}
