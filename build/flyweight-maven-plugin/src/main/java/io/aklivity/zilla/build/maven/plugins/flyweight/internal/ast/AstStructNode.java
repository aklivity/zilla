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
package io.aklivity.zilla.build.maven.plugins.flyweight.internal.ast;

import static io.aklivity.zilla.build.maven.plugins.flyweight.internal.ast.AstAbstractMemberNode.NULL_DEFAULT;
import static java.lang.String.format;
import static java.util.Collections.unmodifiableList;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class AstStructNode extends AstNamedNode
{
    private final int typeId;
    private final AstType supertype;
    private final List<AstStructMemberNode> members;

    @Override
    public <R> R accept(
        Visitor<R> visitor)
    {
        return visitor.visitStruct(this);
    }

    public int typeId()
    {
        return typeId;
    }

    public AstType supertype()
    {
        return supertype;
    }

    public List<AstStructMemberNode> members()
    {
        return members;
    }

    @Override
    public AstNamedNode withName(
        String name)
    {
        return new AstStructNode(name, typeId, supertype, members);
    }

    @Override
    public Kind getKind()
    {
        return Kind.STRUCT;
    }

    @Override
    public int hashCode()
    {
        return supertype != null
               ? (name.hashCode() << 11) ^ supertype.hashCode() << 7 ^ members.hashCode() << 3 ^ typeId
               : (name.hashCode() << 11) ^ members.hashCode() << 3 ^ typeId;
    }

    @Override
    public boolean equals(
        Object o)
    {
        if (o == this)
        {
            return true;
        }

        if (!(o instanceof AstStructNode))
        {
            return false;
        }

        AstStructNode that = (AstStructNode) o;
        return Objects.equals(this.name, that.name) &&
                this.typeId == that.typeId &&
                Objects.equals(this.supertype, that.supertype) &&
                Objects.equals(this.members, that.members);
    }

    private AstStructNode(
        String name,
        int typeId,
        AstType supertype,
        List<AstStructMemberNode> members)
    {
        super(name);
        this.typeId = typeId;
        this.supertype = supertype;
        this.members = unmodifiableList(members);
    }

    public static final class Builder extends AstNamedNode.Builder<AstStructNode>
    {
        private AstType supertype;
        private int typeId;
        private List<AstStructMemberNode> members;

        public Builder()
        {
            this.members = new LinkedList<>();
        }

        public Builder name(
            String name)
        {
            this.name = name;
            return this;
        }

        public Builder typeId(
            int typeId)
        {
            this.typeId = typeId;
            return this;
        }

        public Builder supertype(
            AstType supertype)
        {
            this.supertype = supertype;
            return this;
        }

        public Builder member(
            AstStructMemberNode member)
        {
            if (member.sizeName() != null)
            {
                final String target = member.sizeName();
                Optional<AstStructMemberNode> sizeField = members.stream().filter(n -> target.equals(n.name())).findFirst();
                if (sizeField.isPresent())
                {
                    AstStructMemberNode size = sizeField.get();
                    member.sizeType(size.type());

                    if (size.defaultValue() != null)
                    {
                        throw new IllegalArgumentException(format(
                                "Size field \"%s\" for field \"%s\" must not have a default value's",
                                member.sizeName(), member.name()));
                    }
                    boolean defaultsToNull = member.defaultValue() == NULL_DEFAULT;
                    boolean sizeIsVarint = size.type() == AstType.VARINT32 || size.type() == AstType.VARINT64;
                    if (sizeIsVarint)
                    {
                        if (member.type() == AstType.OCTETS)
                        {
                            size.usedAsSize(true);
                        }
                        else
                        {
                            throw new IllegalArgumentException(format(
                                "Size field \"%s\" for field \"%s\" may not be of type varint",
                                member.sizeName(), member.name()));
                        }
                    }
                    else if (defaultsToNull && !size.type().isSignedInt())
                    {
                        throw new IllegalArgumentException(format(
                                "Size field \"%s\" for field \"%s\" defaulting to null must be a signed integer type",
                                member.sizeName(), member.name()));
                    }
                    else if (!defaultsToNull && !size.type().isUnsignedInt())
                    {
                        throw new IllegalArgumentException(format(
                                "Size field \"%s\" for field \"%s\" must be an unsigned integer type",
                                member.sizeName(), member.name()));
                    }
                    else
                    {
                        size.usedAsSize(true);
                    }
                }
                else
                {
                    throw new IllegalArgumentException(format("Size field \"%s\" not found for field \"%s\"",
                            member.sizeName(), member.name()));
                }
            }
            this.members.add(member);
            return this;
        }

        @Override
        public AstStructNode build()
        {
            return new AstStructNode(name, typeId, supertype, members);
        }
    }
}
