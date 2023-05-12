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
package io.aklivity.zilla.build.maven.plugins.flyweight.internal;

import static org.junit.Assert.assertNotNull;

import java.io.File;

import org.apache.maven.plugin.testing.MojoRule;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class GenerateMojoRule extends MojoRule
{

    private PlexusConfiguration configuration;
    private GenerateMojo mojo;

    public GenerateMojoRule() throws Exception
    {
        File pom = new File("src/test/resources/test-project/pom.xml");
        configuration = extractPluginConfiguration("flyweight-maven-plugin", pom);
    }

    GenerateMojoRule scopeNames(String scopeNames)
    {
        configuration.addChild("scopeNames", scopeNames);
        return this;
    }

    GenerateMojoRule packageName(String packageName)
    {
        configuration.addChild("packageName", packageName);
        return this;
    }

    GenerateMojoRule inputDirectory(String inputDirectory)
    {
        configuration.addChild("inputDirectory", inputDirectory);
        return this;
    }

    GenerateMojoRule outputDirectory(String outputDirectory)
    {
        configuration.addChild("outputDirectory", outputDirectory);
        return this;
    }

    public void generate() throws Exception
    {
        configureMojo(mojo, configuration);
        mojo.execute();
    }

    @Override
    public Statement apply(
        Statement base,
        Description description)
    {
        Statement myStatement = new Statement()
        {

            @Override
            public void evaluate() throws Throwable
            {
                MavenProject project = readMavenProject(new File("src/test/resources/test-project"));
                mojo = (GenerateMojo) lookupConfiguredMojo(project, "generate");
                assertNotNull(mojo);
                base.evaluate();
            }

        };
        return super.apply(myStatement, description);

    }
}
