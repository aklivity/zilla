#!/usr/bin/env python3
# Selects the examples the "testing" job runs for a pull request: an example runs
# when it changed, or when a module it configures was rebuilt by the Maven build
# cache in this run. Everything else is skipped. Pushes, infrastructure changes,
# examples whose modules cannot be derived, and EXAMPLES_SELECTION=all run every
# example. An example listed in MODULES_BY_EXAMPLE depends on exactly the modules it matches.
#
# environment:
#   ALL                JSON array of every example directory
#   EVENT              github.event_name
#   BASE_SHA/HEAD_SHA  pull request range
#   CHANGED_MODULES    comma separated artifactIds rebuilt in this run
#   MODE               EXAMPLES_SELECTION: "all" disables selection
#   GITHUB_OUTPUT      receives matrix_directories=<json array>

import json
import os
import re
import subprocess
import sys

KINDS = ('binding', 'guard', 'catalog', 'vault', 'model', 'exporter', 'metrics', 'store', 'resolver', 'classifier', 'embedding')
MODULE_BASES = ('runtime', 'incubator')
MODULES_BY_EXAMPLE = {'inspect.schema': re.compile(r'\.spec$|^command-inspect$')}
INFRASTRUCTURE = re.compile(
    r'^(pom\.xml|conf/|build/|cloud/|manager/|\.mvn/|mvnw|examples/\.github/|\.github/workflows/build\.yml|'
    r'(runtime|specs|incubator)/pom\.xml)')
EXAMPLE = re.compile(r'^examples/([^/]+)/')
KEYED = re.compile(r'^\s*(?:-\s*)?(?:type|model)\s*:\s*["\']?([A-Za-z0-9_.-]+)')
METRIC = re.compile(r'^\s*-\s*([a-z]+)\.[a-z.]+\s*$')


def list_modules(root):
    return {name
            for base in MODULE_BASES if os.path.isdir(os.path.join(root, base))
            for name in os.listdir(os.path.join(root, base))
            if os.path.isfile(os.path.join(root, base, name, 'pom.xml'))
            and re.match('(%s)-' % '|'.join(KINDS), name)
            and not name.endswith(('.spec', '.conf'))}


def configured_modules(root, example, modules):
    names = set()
    for dirpath, dirnames, filenames in os.walk(os.path.join(root, 'examples', example)):
        dirnames[:] = [d for d in dirnames if d != 'node_modules']
        for filename in filenames:
            if filename.endswith(('.yaml', '.yml')):
                with open(os.path.join(dirpath, filename), errors='ignore') as lines:
                    for line in lines:
                        keyed = KEYED.match(line)
                        metric = METRIC.match(line)
                        if keyed:
                            names.update('%s-%s' % (kind, keyed.group(1)) for kind in KINDS if kind != 'metrics')
                        if metric:
                            names.add('metrics-%s' % metric.group(1))
    return names & modules


def affected(example, configured, built):
    pattern = MODULES_BY_EXAMPLE.get(example)
    return any(pattern.search(m) for m in built) if pattern else not configured or bool(configured & built)


def select(examples, configured, files, built, event, mode):
    if mode == 'all' or event != 'pull_request':
        selected, reason = set(examples), 'selection disabled or not a pull request'
    elif any(INFRASTRUCTURE.match(f) for f in files):
        selected, reason = set(examples), 'build infrastructure changed'
    else:
        changed = {m.group(1) for m in map(EXAMPLE.match, files) if m}
        selected = {e for e in examples if e in changed or affected(e, configured[e], built)}
        reason = 'changed examples, rebuilt modules and examples without derivable modules'
    return sorted(selected), reason


def changed_files(base, head):
    return subprocess.run(['git', 'diff', '--name-only', '%s...%s' % (base, head)],
                          capture_output=True, text=True, check=True).stdout.split()


def main():
    root = os.environ.get('GITHUB_WORKSPACE', '.')
    examples = json.loads(os.environ['ALL'])
    event = os.environ.get('EVENT', '')
    modules = list_modules(root)
    configured = {e: configured_modules(root, e, modules) for e in examples}
    built = {m for m in os.environ.get('CHANGED_MODULES', '').split(',') if m}
    files = changed_files(os.environ['BASE_SHA'], os.environ['HEAD_SHA']) if event == 'pull_request' else []
    selected, reason = select(examples, configured, files, built, event, os.environ.get('MODE', ''))
    print('%d of %d examples selected: %s' % (len(selected), len(examples), reason))
    print('rebuilt modules: %s' % (', '.join(sorted(built)) or 'none'))
    print('selected: %s' % ', '.join(selected))
    print('skipped: %s' % ', '.join(sorted(set(examples) - set(selected))))
    with open(os.environ.get('GITHUB_OUTPUT', os.devnull), 'a') as output:
        output.write('matrix_directories=%s\n' % json.dumps(selected))


if __name__ == '__main__':
    sys.exit(main())
