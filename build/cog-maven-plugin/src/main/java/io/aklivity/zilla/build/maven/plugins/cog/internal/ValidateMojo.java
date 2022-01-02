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
package io.aklivity.zilla.build.maven.plugins.cog.internal;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;
import static org.apache.maven.plugins.annotations.LifecyclePhase.PROCESS_RESOURCES;
import static org.apache.maven.plugins.annotations.ResolutionScope.COMPILE;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CancellationException;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;

@Mojo(name = "validate",
      defaultPhase = PROCESS_RESOURCES,
      requiresDependencyResolution = COMPILE,
      requiresProject = true)
public final class ValidateMojo extends AbstractMojo
{
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException
    {
        try
        {
            executeImpl();
        }
        catch (IOException | CancellationException e)
        {
            throw new MojoFailureException("Validate failed", e);
        }
    }

    protected void executeImpl() throws IOException
    {
        List<String> targetScopes = unmodifiableList(asList(scopeNames.split("\\s+")));
        parseAST(targetScopes);
    }
}
