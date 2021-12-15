/*
 * Copyright 2021-2021 Aklivity Inc.
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
package io.aklivity.zilla.build.maven.plugins.cog.internal;

import static org.apache.maven.plugins.annotations.LifecyclePhase.GENERATE_TEST_SOURCES;
import static org.apache.maven.plugins.annotations.ResolutionScope.TEST;

import java.io.File;
import java.io.IOException;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "test-generate",
      defaultPhase = GENERATE_TEST_SOURCES,
      requiresDependencyResolution = TEST,
      requiresProject = true)
public final class TestGenerateMojo extends AbstractMojo
{
    @Parameter(defaultValue = "")
    protected String testPackageName;

    @Parameter(defaultValue = "${project.build.directory}/generated-test-sources/zilla")
    protected File testOutputDirectory;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException
    {
        try
        {
            executeImpl();
        }
        catch (IOException e)
        {
            throw new MojoFailureException("Unable to generate test sources", e);
        }
    }

    @Override
    protected void executeImpl() throws IOException
    {
        Generator generator = new Generator();
        generator.debug(getLog()::debug);
        generator.error(getLog()::error);
        generator.warn(getLog()::warn);
        generator.setPackageName(testPackageName);
        generator.setInputDirectory(inputDirectory);
        generator.setOutputDirectory(testOutputDirectory);
        generator.setScopeNames(scopeNames);
        generator.generate(createLoader());
        project.addTestCompileSourceRoot(testOutputDirectory.getPath());
    }
}
