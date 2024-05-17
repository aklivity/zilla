/*
 * Copyright 2021-2023 Aklivity Inc
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
package io.aklivity.zilla.runtime.command.dump.internal.test;

import static org.agrona.LangUtil.rethrowUnchecked;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class Clean implements TestRule
{
    private static final Path ENGINE_PATH = Path.of("target/zilla-itests");

    public Statement apply(
        Statement base,
        Description description)
    {
        return new Statement()
        {
            @Override
            public void evaluate() throws Throwable
            {
                base.evaluate();
                clean();
            }
        };
    }

    public static void clean()
    {
        System.out.println("clean dir");
        System.out.println(ENGINE_PATH);
        if (Files.exists(ENGINE_PATH) && Files.isDirectory(ENGINE_PATH)) {

            try {
                Files.delete(ENGINE_PATH.resolve("labels"));
                /*Files.walkFileTree(ENGINE_PATH, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
                    {
                        System.out.println("delete file " + file);
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }
                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        //Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    }
                });*/
            } catch (Exception ex) {
                rethrowUnchecked(ex);
            }
        }
    }
}
