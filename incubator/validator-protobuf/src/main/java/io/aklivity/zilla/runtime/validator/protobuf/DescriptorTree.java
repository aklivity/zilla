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
package io.aklivity.zilla.runtime.validator.protobuf;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FileDescriptor;

public class DescriptorTree
{
    protected final Map<String, DescriptorTree> children;
    protected final List<Integer> indexes;

    protected Descriptors.Descriptor descriptor;
    protected String name;

    private DescriptorTree()
    {
        this.children = new LinkedHashMap<>();
        this.indexes = new LinkedList<>();
    }

    protected DescriptorTree(
        FileDescriptor fileDescriptors)
    {
        this();
        this.name = fileDescriptors.getPackage();
        for (Descriptor descriptor : fileDescriptors.getMessageTypes())
        {
            addDescriptor(descriptor);
            addNestedDescriptors(descriptor);
        }
    }

    protected DescriptorTree findByName(
        String path)
    {
        DescriptorTree current = this;
        int start = 0;
        int end;

        while (start < path.length())
        {
            end = path.indexOf('.', start);
            if (end == -1)
            {
                end = path.length();
            }

            String part = path.substring(start, end);
            current = current.children.get(part);

            if (current == null)
            {
                break;
            }
            start = end + 1;
        }
        return current;
    }

    protected Descriptor findByIndexes(
        List<Integer> indexes)
    {
        DescriptorTree current = this;

        for (Integer index : indexes)
        {
            current = current.findChild(index);
            if (current == null)
            {
                break;
            }
        }
        return current != null ? current.descriptor : null;
    }

    private DescriptorTree findParent(
        String path)
    {
        int index = path.lastIndexOf('.');
        String part = index >= 0 ? path.substring(index + 1) : path;
        return this.children.getOrDefault(part, null);
    }

    private DescriptorTree findChild(
        int index)
    {
        DescriptorTree tree = this;
        int currentIndex = 0;
        for (Map.Entry<String, DescriptorTree> entry : children.entrySet())
        {
            if (currentIndex == index)
            {
                tree = entry.getValue();
                break;
            }
            currentIndex++;
        }
        return tree;
    }

    private void addNestedDescriptor(
        Descriptor parent,
        int index)
    {
        DescriptorTree parentNode = findParent(parent.getFullName());
        if (parentNode != null)
        {
            Descriptors.Descriptor nestedDescriptor = parent.getNestedTypes().get(index);
            parentNode.addDescriptor(nestedDescriptor);
            parentNode.addNestedDescriptors(nestedDescriptor);
        }
    }

    private void addDescriptor(
        Descriptor descriptor)
    {
        DescriptorTree node = new DescriptorTree();
        node.descriptor = descriptor;
        node.name = name;
        node.indexes.addAll(this.indexes);
        node.indexes.add(this.children.size());
        this.children.put(descriptor.getName(), node);
    }

    private void addNestedDescriptors(Descriptor descriptor)
    {
        for (int i = 0; i < descriptor.getNestedTypes().size(); i++)
        {
            addNestedDescriptor(descriptor, i);
        }
    }
}
