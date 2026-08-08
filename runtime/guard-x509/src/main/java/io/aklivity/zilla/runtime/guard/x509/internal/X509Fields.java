/*
 * Copyright 2021-2026 Aklivity Inc
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
package io.aklivity.zilla.runtime.guard.x509.internal;

import static java.util.Locale.ROOT;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.security.auth.x500.X500Principal;

// Flattens a leaf certificate into the field vocabulary shared by identity, attributes and roles.
// Field paths are internal; a field carrying no value on the certificate is simply absent, so
// every lookup is total and an absent field can never match.
final class X509Fields
{
    static final String SUBJECT_DN = "subject.dn";
    static final String ISSUER_DN = "issuer.dn";
    static final String X5T_S256 = "x5t.s256";

    static final String SAN_EMAIL = "san.email";
    static final String SAN_DNS = "san.dns";
    static final String SAN_URI = "san.uri";
    static final String SAN_IP = "san.ip";

    private static final String SUBJECT_PREFIX = "subject.";
    private static final String ISSUER_PREFIX = "issuer.";

    private static final int SAN_TYPE_RFC822_NAME = 1;
    private static final int SAN_TYPE_DNS_NAME = 2;
    private static final int SAN_TYPE_URI = 6;
    private static final int SAN_TYPE_IP_ADDRESS = 7;

    private static final String DIGEST_ALGORITHM = "SHA-256";

    private X509Fields()
    {
    }

    static Map<String, List<String>> resolve(
        X509Certificate leaf)
    {
        Map<String, List<String>> fields = new LinkedHashMap<>();

        resolveName(fields, SUBJECT_DN, SUBJECT_PREFIX, leaf.getSubjectX500Principal());
        resolveName(fields, ISSUER_DN, ISSUER_PREFIX, leaf.getIssuerX500Principal());
        resolveSubjectAltNames(fields, leaf);
        resolveThumbprint(fields, leaf);

        return fields;
    }

    private static void resolveName(
        Map<String, List<String>> fields,
        String dnField,
        String prefix,
        X500Principal principal)
    {
        add(fields, dnField, principal.getName(X500Principal.CANONICAL));

        try
        {
            LdapName name = new LdapName(principal.getName(X500Principal.RFC2253));

            for (Rdn rdn : name.getRdns())
            {
                Attributes attributes = rdn.toAttributes();

                NamingEnumeration<? extends Attribute> types = attributes.getAll();
                while (types.hasMore())
                {
                    Attribute attribute = types.next();
                    String field = prefix + attribute.getID().toLowerCase(ROOT);

                    NamingEnumeration<?> values = attribute.getAll();
                    while (values.hasMore())
                    {
                        Object value = values.next();
                        if (value instanceof String)
                        {
                            add(fields, field, (String) value);
                        }
                    }
                }
            }
        }
        catch (NamingException ex)
        {
            // a distinguished name that does not parse contributes no relative fields
        }
    }

    private static void resolveSubjectAltNames(
        Map<String, List<String>> fields,
        X509Certificate leaf)
    {
        try
        {
            Collection<List<?>> names = leaf.getSubjectAlternativeNames();

            if (names != null)
            {
                for (List<?> name : names)
                {
                    if (name.size() >= 2 &&
                        name.get(0) instanceof Integer type &&
                        name.get(1) instanceof String value)
                    {
                        String field = asSubjectAltNameField(type);
                        if (field != null)
                        {
                            add(fields, field, value);
                        }
                    }
                }
            }
        }
        catch (CertificateParsingException ex)
        {
            // an unparseable extension contributes no subject alternative name fields
        }
    }

    private static void resolveThumbprint(
        Map<String, List<String>> fields,
        X509Certificate leaf)
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance(DIGEST_ALGORITHM);
            byte[] thumbprint = digest.digest(leaf.getEncoded());

            add(fields, X5T_S256, Base64.getUrlEncoder().withoutPadding().encodeToString(thumbprint));
        }
        catch (CertificateEncodingException | NoSuchAlgorithmException ex)
        {
            // a certificate that cannot be re-encoded contributes no thumbprint field
        }
    }

    private static String asSubjectAltNameField(
        int type)
    {
        return switch (type)
        {
        case SAN_TYPE_RFC822_NAME -> SAN_EMAIL;
        case SAN_TYPE_DNS_NAME -> SAN_DNS;
        case SAN_TYPE_URI -> SAN_URI;
        case SAN_TYPE_IP_ADDRESS -> SAN_IP;
        default -> null;
        };
    }

    private static void add(
        Map<String, List<String>> fields,
        String field,
        String value)
    {
        fields.computeIfAbsent(field, name -> new LinkedList<>()).add(value);
    }
}
