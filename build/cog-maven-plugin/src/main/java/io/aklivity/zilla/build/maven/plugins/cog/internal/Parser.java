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

import static java.util.stream.Collectors.toSet;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Consumer;

import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.ParseCancellationException;

import io.aklivity.zilla.build.maven.plugins.cog.internal.ast.AstNode;
import io.aklivity.zilla.build.maven.plugins.cog.internal.ast.AstSpecificationNode;
import io.aklivity.zilla.build.maven.plugins.cog.internal.ast.AstStructNode;
import io.aklivity.zilla.build.maven.plugins.cog.internal.ast.AstType;
import io.aklivity.zilla.build.maven.plugins.cog.internal.ast.parse.AstParser;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogLexer;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser;
import io.aklivity.zilla.build.maven.plugins.cog.internal.parser.CogParser.SpecificationContext;

class Parser
{
    private static final Consumer<String> NO_OP = s -> {};

    private Consumer<String> error = System.err::println;
    private Consumer<String> warn = NO_OP;
    private Consumer<String> debug = NO_OP;

    Parser()
    {

    }

    Parser debug(Consumer<String> debug)
    {
        this.debug = debug;
        return this;
    }

    Parser error(Consumer<String> error)
    {
        this.error = error;
        return this;
    }

    Parser warn(Consumer<String> warn)
    {
        this.warn = warn;
        return this;
    }

    final List<AstSpecificationNode> parseAST(
        List<String> targetScopes, ClassLoader loader) throws IOException
    {
        List<AstSpecificationNode> specifications = new LinkedList<>();
        SortedSet<String> parsedResourceNames = new TreeSet<>();
        Set<String> remainingScopes = new LinkedHashSet<>(targetScopes);
        while (!remainingScopes.isEmpty())
        {
            String remainingScope = remainingScopes.iterator().next();
            remainingScopes.remove(remainingScope);
            String resourceName = remainingScope.replaceAll("([^:]+).*", "$1.idl");
            if (parsedResourceNames.add(resourceName))
            {
                debug.accept("loading: " + resourceName);

                URL resource = loader.getResource(resourceName);
                if (resource == null)
                {
                    warn.accept(String.format("Resource %s not found", resourceName));
                    continue;
                }

                AstSpecificationNode specification = parseSpecification(resourceName, resource);
                specifications.add(specification);

                Set<String> referencedTypes = specification.accept(new ReferencedTypeResolver());
                debug.accept("referenced types: " + referencedTypes);

                String regex = "((:?[^:]+(?:\\:\\:[^:]+)*)?)\\:\\:[^:]+";
                Set<String> referencedScopes = referencedTypes.stream()
                                                              .map(t -> t.replaceAll(regex, "$1"))
                                                              .collect(toSet());
                debug.accept("referenced scopes: " + referencedScopes);

                remainingScopes.addAll(referencedScopes);
            }
        }
        return specifications;
    }

    private AstSpecificationNode parseSpecification(
        String resourceName,
        URL resource) throws IOException
    {
        try (InputStream input = resource.openStream())
        {
            CharStream chars = CharStreams.fromStream(input);
            CogLexer lexer = new CogLexer(chars);
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            CogParser parser = new CogParser(tokens);
            parser.setErrorHandler(new BailErrorStrategy());

            SpecificationContext ctx = parser.specification();
            return new AstParser().visitSpecification(ctx);
        }
        catch (ParseCancellationException ex)
        {
            Throwable cause = ex.getCause();
            if (cause instanceof RecognitionException)
            {
                RecognitionException re = (RecognitionException) cause;
                Token token = re.getOffendingToken();
                if (token != null)
                {
                    String message = String.format("Parse failed in %s at %d:%d on \"%s\"",
                            resourceName, token.getLine(), token.getCharPositionInLine(), token.getText());
                    error.accept(message);
                }
            }

            throw ex;
        }
    }

    private static final class ReferencedTypeResolver extends AstNode.Visitor<Set<String>>
    {
        private final Set<String> qualifiedNames = new HashSet<>();

        @Override
        public Set<String> visitStruct(
            AstStructNode structNode)
        {
            AstType supertype = structNode.supertype();
            if (supertype != null)
            {
                qualifiedNames.add(supertype.name());
            }

            return super.visitStruct(structNode);
        }

        @Override
        protected Set<String> defaultResult()
        {
            return qualifiedNames;
        }
    }




}
