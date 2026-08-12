---
name: write-k3po-it
description: Wire up a new k3po-based *IT.java test class (K3poRule + JUnit 5 rule-migration support) for a spec or runtime module. Use when creating the first IT class in a spec/runtime module, or any new IT class that drives .rpt scripts.
---

# Writing a k3po IT class

The `.rpt` scripts are driven by [k3po](https://github.com/k3po/k3po), which
integrates via a JUnit `@Rule` (`K3poRule`). JUnit `@Rule` is a JUnit 4
construct. IT classes must therefore enable JUnit 4 rule migration support
when running under JUnit 5:

```java
@ExtendWith(EngineExtension.class)
@EnableRuleMigrationSupport          // required for K3poRule under JUnit 5
class HttpRequestIT
{
    @Rule
    public final K3poRule k3po = new K3poRule().addScriptRoot("specs", "io/aklivity/zilla/specs/binding/http");

    @Test
    @Specification({ "client.request/client", "client.request/server" })
    public void shouldReceiveClientRequest() throws Exception
    {
        k3po.finish();
    }
}
```

Do not attempt to replace `K3poRule` with a JUnit 5 extension — k3po's
script execution lifecycle is bound to the `@Rule` contract. The
`@EnableRuleMigrationSupport` annotation from
`org.junit.jupiter:junit-jupiter-migrationsupport` is the correct and only
approach.

`addScriptRoot` points at the spec project's script package
(`io/aklivity/zilla/specs/binding/<n>` or `.../engine` etc.), and
`@Specification` references scripts as `${net}/scenario/client` /
`${net}/scenario/server` when the IT class declares `net`/`app` root
variables — see [specs/AGENTS.md](../../../specs/AGENTS.md) for the script
folder layout and naming conventions this wiring assumes.
