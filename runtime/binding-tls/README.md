# binding-tls

The public configuration reference for this binding is published at
https://docs.aklivity.io/zilla/latest/reference/config/bindings/binding-tls.html.
This file documents the parts of the binding whose rationale is not obvious from
the schema alone.

## Selecting a client certificate — `routes[].with.certificate`

A `tls` client route may name the certificate the binding should present when the
far end requests client authentication:

```yaml
guards:
  x509:
    type: x509
    options:
      identity: subject.cn
      roles:
        client:
          - issuer.cn: Test Client CA
            subject.cn: client-*

bindings:
  tls_client0:
    type: tls
    kind: client
    vault: upstream
    options:
      signers:
        - upstream-ca
      trust:
        - broker-ca
    routes:
      - when:
          - authority: broker.example.com
        guarded:
          x509:
            - client
        with:
          certificate:
            subject.cn: ${guarded['x509'].identity}
        exit: tcp_client0
```

Two properties are supported, `subject.cn` and `subject.dn`. They are matched
against the corresponding properties of each candidate key already offered by the
vault. Multiple properties are AND'd — a candidate must match all of them.

Values are either literals (`subject.cn: kafka-gateway`, always present this
certificate) or `${guarded['name'].identity}` / `${guarded['name'].attributes.*}`
expressions, resolved per stream from the guard session identified by the inbound
stream's `authorization`.

`with` is accepted on the `client` kind only, and is rejected on `server` and
`proxy`.

### What this feature does and does not decide

It decides *which identity to assert*, not *which certificate is usable*. Key
algorithm, issuer, validity period, key usage and the `clientAuth` extended key
usage are already applied by the PKIX key manager before selection runs, and
`options.signers` already scopes the candidate set.

If no candidate matches, no certificate is presented. Whether that is fatal is up
to the far end: a server that requires client authentication will fail the
handshake, and one that does not will complete it.

When more than one candidate matches, every one of them satisfies the route, so
the choice between them is immaterial to the configuration — but it must not vary
between runs. Keystore enumeration order carries no such guarantee, so selection
takes the candidate with the latest `notBefore`, breaking ties on thumbprint.
That also prefers the new certificate while an old one is still valid during a
rotation, which is the usual reason two candidates match at once.

### Behaviour on misconfiguration

- A `${guarded['name']...}` expression naming a guard that does not resolve is a
  configuration error, reported when the binding is attached. Left unchecked it
  would select no certificate on every stream and surface only as a handshake
  failure at the far end.
- An expression that resolves to no value at runtime emits a
  `binding.tls.client.certificate.not.resolved` event naming the property that
  did not resolve, and no certificate is presented.
- A selection that matches no candidate key emits a
  `binding.tls.client.certificate.not.matched` event naming the whole selector,
  and no certificate is presented. Without it a no-match is visible only as a
  handshake failure at the far end, with nothing logged locally.

A literal `subject.dn` is canonicalized when the configuration is read, using the
same renderer that produces the `subject.dn` property of a certificate. Canonical
form lowercases attribute values and normalizes ordering and whitespace, so
without this a hand-written `subject.dn: "CN=Foo,OU=Bar"` would never compare
equal to a guard-derived value.

## Why the server uses `options.authorization` and the client uses `routes[].with`

These are not two styles for one concept. They sit at different points in the
connection lifecycle, and the timing forces the shape.

On the **client**, every route-matching input precedes the handshake.
`TlsBindingConfig.resolve` matches on `authority`, `alpn` and `port`, all read
from the inbound application stream's `ProxyBeginEx` — what the upstream binding
is requesting, not what the upstream server negotiates — plus `authorization`,
already present on the BEGIN frame. `TlsClientFactory` resolves the route and only
then creates the `SSLEngine`, so the route is fully determined before the first
ClientHello byte and `with` can parameterize the handshake.

On the **server** it cannot be. The server's route depends on SNI and ALPN, which
arrive inside the ClientHello, and on `guarded`, which depends on the client
certificate and is therefore known only after the handshake. That is what
`resolvePortOnlyBeforeHandshake` exists for. A `with` clause on a `tls` server
route would be resolved too late to shape its own handshake.

So the server keeps `options.authorization`, binding-wide and evaluated during and
after the handshake, and the client gets `routes[].with`, route-scoped and resolved
before it. Being route-scoped is also a capability `options` structurally cannot
have: different upstream clusters selected by authority or ALPN, each with its own
certificate policy.

## Relationship to `secure.name`

With no `with.certificate` on the matched route, the client falls back to selecting
on the `secure.name` info item of the inbound `ProxyBeginEx`, as before.

Prefer `with.certificate`. `secure.name` is an ordinary `ProxyBeginEx` field, so any
binding upstream of the `tls` client that can populate one — including a `proxy`
binding decoding PROXY protocol v2 from an untrusted peer — determines which
certificate Zilla presents upstream. There is no structural link between "a chain
was verified" and "this identity is asserted". A guard session id cannot be forged
that way. `secure.name` also has to be relayed explicitly by every intermediate
binding, and can express nothing but a common name.

One caveat applies to the `with.certificate` route as well: `authorization` is a
single slot. A binding between the two `tls` bindings that runs its own guard
replaces the session, so a pipeline with, say, an external SASL guard in front of a
`tls` client expecting the x509 session will present the wrong certificate.

## Run performance benchmark

```
./mvnw clean install
```

```
cd target
java -jar ./binding-tls-develop-SNAPSHOT-shaded-tests.jar TlsHandshakeBM
```
