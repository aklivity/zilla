// Minimal mock of the subset of the GitHub REST API used by the mcp_http
// "github" toolkit. Requires a Bearer credential on every request -- Zilla
// forwards the caller's own JWT via options.authorization.credentials.headers
// on the mcp_http binding -- and echoes the forwarded x-user-identity header
// into the response so the credential-forwarding path is directly observable.

import express from "express";

const PORT = Number(process.env.PORT ?? 4001);

const app = express();
app.use(express.json());

let nextNumber = 101;

app.post("/repos/:owner/:repo/pulls", (req, res) =>
{
    const authorization = req.headers["authorization"] ?? "";
    if (!authorization.startsWith("Bearer "))
    {
        res.status(401).json({ message: "Bad credentials" });
        return;
    }

    const { owner, repo } = req.params;
    const { title, head, base } = req.body ?? {};
    const number = nextNumber++;

    res.status(201).json({
        number,
        html_url: `https://github.com/${owner}/${repo}/pull/${number}`,
        state: "open",
        title,
        head,
        base,
        opened_by: req.headers["x-user-identity"] ?? "unknown"
    });
});

app.listen(PORT, () => console.log(`ghapi mock listening on ${PORT}`));
