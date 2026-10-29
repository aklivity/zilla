import importlib.util
import os
import unittest

spec = importlib.util.spec_from_file_location('select_examples', os.path.join(os.path.dirname(__file__), 'select-examples.py'))
select_examples = importlib.util.module_from_spec(spec)
spec.loader.exec_module(select_examples)

EXAMPLES = ['http.echo', 'inspect.schema', 'mqtt.proxy', 'kafka.proxy', 'opaque']
CONFIGURED = {
    'http.echo': {'binding-tcp', 'binding-http'},
    'mqtt.proxy': {'binding-tcp', 'binding-mqtt'},
    'kafka.proxy': {'binding-tcp', 'binding-kafka', 'model-json'},
    'inspect.schema': {'binding-tcp'},
    'opaque': set(),
}


def select(files, built, event='pull_request', mode=''):
    return select_examples.select(EXAMPLES, CONFIGURED, files, set(built), event, mode)[0]


class SelectExamplesTest(unittest.TestCase):

    def test_should_select_examples_configuring_rebuilt_module(self):
        self.assertEqual(['mqtt.proxy', 'opaque'], select(['runtime/binding-mqtt/A.java'], ['binding-mqtt']))

    def test_should_select_every_configuring_example_for_shared_module(self):
        self.assertEqual(
            ['http.echo', 'kafka.proxy', 'mqtt.proxy', 'opaque'],
            select(['runtime/binding-tcp/A.java'], ['binding-tcp']))

    def test_should_select_changed_example(self):
        self.assertEqual(['http.echo', 'opaque'], select(['examples/http.echo/etc/zilla.yaml'], []))

    def test_should_skip_documentation_changes(self):
        self.assertEqual(['opaque'], select(['README.md', 'examples/README.md'], []))

    def test_should_select_all_for_build_infrastructure(self):
        for path in ('pom.xml', 'cloud/docker-image/pom.xml', '.github/workflows/build.yml', 'examples/.github/test-lib.sh'):
            self.assertEqual(sorted(EXAMPLES), select([path], ['binding-mqtt']), path)

    def test_should_follow_rebuilt_modules_for_lines_only_added_to_image_wiring(self):
        changes = select_examples.parse_numstat(
            '12\t0\tcloud/docker-image/pom.xml\n3\t0\tcloud/docker-image/src/main/docker/zpm.json.template\n')
        self.assertEqual([], select_examples.selectable_files(changes))
        self.assertEqual(['opaque'], select(select_examples.selectable_files(changes), []))

    def test_should_select_all_for_lines_removed_from_image_wiring(self):
        changes = select_examples.parse_numstat('2\t1\tcloud/docker-image/pom.xml\n')
        self.assertEqual(['cloud/docker-image/pom.xml'], select_examples.selectable_files(changes))
        self.assertEqual(sorted(EXAMPLES), select(select_examples.selectable_files(changes), []))

    def test_should_select_all_for_lines_only_added_to_other_cloud_files(self):
        changes = select_examples.parse_numstat('4\t0\tcloud/docker-image/src/main/docker/Dockerfile\n')
        self.assertEqual(sorted(EXAMPLES), select(select_examples.selectable_files(changes), []))

    def test_should_select_rebuilt_modules_for_aggregator_pom_change(self):
        for path in ('runtime/pom.xml', 'specs/pom.xml', 'incubator/pom.xml'):
            self.assertEqual(['mqtt.proxy', 'opaque'], select([path], ['binding-mqtt']), path)

    def test_should_skip_examples_for_aggregator_pom_change_without_rebuilt_modules(self):
        self.assertEqual(['opaque'], select(['incubator/pom.xml'], []))

    def test_should_skip_examples_when_sources_changed_without_rebuilt_modules(self):
        self.assertEqual(['opaque'], select(['runtime/binding-mqtt/A.java'], []))

    def test_should_skip_examples_for_notice_change(self):
        self.assertEqual(['opaque'], select(['incubator/NOTICE'], []))

    def test_should_select_inspect_schema_for_rebuilt_spec_or_inspect_command(self):
        for module in ('binding-mqtt.spec', 'engine.spec', 'command-inspect'):
            self.assertIn('inspect.schema', select(['x/%s/A.java' % module], [module]), module)

    def test_should_skip_inspect_schema_for_rebuilt_runtime_module(self):
        self.assertNotIn('inspect.schema', select(['runtime/binding-tcp/A.java'], ['binding-tcp']))

    def test_should_skip_examples_for_rebuilt_unused_command(self):
        self.assertEqual(['opaque'], select(['incubator/command-dump/A.java'], ['command-dump']))

    def test_should_select_all_for_push(self):
        self.assertEqual(sorted(EXAMPLES), select([], [], event='push'))

    def test_should_select_all_when_selection_disabled(self):
        self.assertEqual(sorted(EXAMPLES), select(['README.md'], [], mode='all'))


if __name__ == '__main__':
    unittest.main()
