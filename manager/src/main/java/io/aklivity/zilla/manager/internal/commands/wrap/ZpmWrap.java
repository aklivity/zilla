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
package io.aklivity.zilla.manager.internal.commands.wrap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import com.github.rvesse.airline.annotations.Command;
import com.github.rvesse.airline.annotations.Option;

import io.aklivity.zilla.manager.internal.ZpmCommand;
import io.aklivity.zilla.manager.internal.types.ZpmPathConverterProvider;

@Command(
    name = "wrap",
    description = "Generate wrapper")
public class ZpmWrap extends ZpmCommand
{
    @Option(name = { "--remote-repository" },
            description = "Remote Maven repository")
    public String repoURL = "https://maven.packages.aklivity.io";

    @Option(name = { "--local-repository" },
            description = "Local Maven repository")
    public Path localRepoDir = Paths.get("$HOME/.m2/repository");

    @Option(name = { "--version" },
            description = "Require specific zpm version")
    public String version = VERSION;

    @Option(name = { "--zpmw-directory" },
            description = "zpmw directory",
            typeConverterProvider = ZpmPathConverterProvider.class,
            hidden = true)
    public Path zpmwDir = Paths.get(".zpmw");

    @Override
    public void invoke()
    {
        task:
        try
        {
            if (version == null)
            {
                System.out.println("version not specified");
                break task;
            }

            generateWrapper();
        }
        catch (Exception ex)
        {
            throw new RuntimeException(ex);
        }
    }

    private void generateWrapper() throws IOException
    {
        Path zpmwPath = launcherDir.resolve("zpmw");

        Path localPath = localRepoDir.resolve("io/aklivity/zilla/manager/$version/manager-$version.jar");
        Path wrappedPath = outputDir.resolve("wrapper").resolve("manager-$version.jar");
        String wrappedURL = String.format("%s/io/aklivity/zilla/manager/$version/manager-$version.jar", repoURL);

        Files.write(zpmwPath, Arrays.asList(
                "#!/bin/sh",
                String.format("version=\"%s\"", version),
                String.format("localPath=\"%s\"", localPath),
                String.format("wrappedPath=\"%s\"", wrappedPath),
                String.format("wrappedURL=\"%s\"", wrappedURL),
                "if [ ! -r \"$wrappedPath\" ]; then",
                  "mkdir -p `dirname $wrappedPath`",
                  "if [ -r \"$localPath\" ]; then",
                    "echo $wrappedPath not found, copying from $localPath",
                    "cp $localPath $wrappedPath",
                  "else",
                    "echo $wrappedPath not found, downloading from $wrappedURL",
                    "if command -v wget > /dev/null; then",
                      "wget -O \"$wrappedPath\" \"$wrappedURL\" -nv",
                    "else",
                      "echo wget missing, download failed",
                    "fi",
                  "fi",
                "fi",
                "java $JAVA_OPTIONS -jar \"$wrappedPath\" \"$@\""));
        zpmwPath.toFile().setExecutable(true);
    }
}
