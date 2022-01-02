/*
 * Copyright 2021-2022 Aklivity Inc.
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
package io.aklivity.zilla.manager.internal;

import java.nio.file.Path;
import java.nio.file.Paths;

import javax.inject.Inject;

import com.github.rvesse.airline.HelpOption;
import com.github.rvesse.airline.annotations.Option;

import io.aklivity.zilla.manager.internal.types.ZpmPathConverterProvider;

public abstract class ZpmCommand implements Runnable
{
    public static final String VERSION = ZpmCommand.class.getPackage().getImplementationVersion();

    @Inject
    public HelpOption<ZpmCommand> helpOption;

    @Option(name = { "--silent" },
            hidden = true)
    public Boolean silent = false;

    @Option(name = { "--settings-directory" },
            description = "settings directory",
            typeConverterProvider = ZpmPathConverterProvider.class)
    public Path settingsDir = Paths.get(System.getProperty("user.home"), ".zpm");

    @Option(name = { "--config-directory" },
            description = "config directory",
            typeConverterProvider = ZpmPathConverterProvider.class)
    public Path configDir = Paths.get("");

    @Option(name = { "--output-directory" },
            description = "output directory",
            typeConverterProvider = ZpmPathConverterProvider.class)
    public Path outputDir = Paths.get(".zpm");

    @Option(name = { "--lock-directory" },
            description = "lock directory",
            typeConverterProvider = ZpmPathConverterProvider.class,
            hidden = true)
    public Path lockDir;

    @Option(name = { "--launcher-directory" },
            description = "launcher directory",
            typeConverterProvider = ZpmPathConverterProvider.class)
    public Path launcherDir = Paths.get("");

    protected Path cacheDir;
    protected Path modulesDir;
    protected Path imageDir;
    protected Path generatedDir;

    @Override
    public void run()
    {
        if (!helpOption.showHelpIfRequested())
        {
            if (lockDir == null)
            {
                lockDir = configDir;
            }

            cacheDir = outputDir.resolve("cache");
            modulesDir = outputDir.resolve("modules");
            generatedDir = outputDir.resolve("generated");
            imageDir = outputDir.resolve("image");

            invoke();
        }
    }

    protected abstract void invoke();
}
