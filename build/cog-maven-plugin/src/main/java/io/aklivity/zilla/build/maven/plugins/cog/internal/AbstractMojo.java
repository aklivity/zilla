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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.LinkedList;
import java.util.List;

import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import io.aklivity.zilla.build.maven.plugins.cog.internal.ast.AstSpecificationNode;

public abstract class AbstractMojo extends org.apache.maven.plugin.AbstractMojo
{
    @Parameter(defaultValue = "${project}", readonly = true)
    protected MavenProject project;

    @Parameter(defaultValue = "src/main/zilla")
    protected File inputDirectory;

    @Parameter(defaultValue = "src/main/resources/META-INF/zilla")
    protected File metaDirectory;

    @Parameter(required = true)
    protected String scopeNames;

    private Parser parser = new Parser()
            .debug(getLog()::debug)
            .error(getLog()::error)
            .warn(getLog()::warn);

    protected abstract void executeImpl() throws IOException;

    protected final List<AstSpecificationNode> parseAST(
        List<String> targetScopes) throws IOException
    {
        return parser.parseAST(targetScopes, createLoader());
    }

    ClassLoader createLoader() throws IOException
    {
        List<URL> resourcePath = new LinkedList<>();

        resourcePath.add(inputDirectory.getAbsoluteFile().toURI().toURL());
        resourcePath.add(metaDirectory.getAbsoluteFile().toURI().toURL());

        try
        {
            for (Object resourcePathEntry : project.getTestClasspathElements())
            {
                File resourcePathFile = new File(resourcePathEntry.toString());
                URI resourcePathURI = resourcePathFile.getAbsoluteFile().toURI();
                resourcePath.add(URI.create(String.format("jar:%s!/META-INF/zilla/", resourcePathURI)).toURL());
            }
        }
        catch (DependencyResolutionRequiredException e)
        {
            throw new IOException(e);
        }

        getLog().debug("resource path: " + resourcePath);

        ClassLoader parent = getClass().getClassLoader();
        return new URLClassLoader(resourcePath.toArray(new URL[resourcePath.size()]), parent);
    }
}
