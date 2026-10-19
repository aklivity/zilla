/*
 * Copyright 2021-2026 Aklivity Inc.
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
package io.aklivity.zilla.build.maven.plugins.zpm.internal;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

final class ZpmLauncher
{
    private static final String MAIN_CLASS_NAME = "io.aklivity.zilla.manager.internal.ZpmMain";

    private final File manager;

    ZpmLauncher(
        File manager)
    {
        this.manager = manager;
    }

    void run(
        List<String> args) throws Exception
    {
        Thread thread = Thread.currentThread();
        ClassLoader context = thread.getContextClassLoader();

        // zpm is built on a newer Maven Resolver than the one Maven exposes to plugin class realms,
        // so run it from its own class loader rather than the plugin realm
        try (URLClassLoader loader = new URLClassLoader(
            new URL[] { manager.toURI().toURL() }, ClassLoader.getPlatformClassLoader()))
        {
            thread.setContextClassLoader(loader);
            Method main = loader.loadClass(MAIN_CLASS_NAME).getMethod("main", String[].class);
            main.invoke(null, (Object) args.toArray(String[]::new));
        }
        catch (InvocationTargetException ex)
        {
            throw ex.getCause() instanceof Exception cause ? cause : ex;
        }
        finally
        {
            thread.setContextClassLoader(context);
        }
    }
}
