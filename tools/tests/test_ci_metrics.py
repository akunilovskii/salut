import copy
import unittest

from tools.ci_metrics import baseline, compare, compare_cache


def report(mode, duration=100):
    return dict(schemaVersion=1, completed=True, fullSuite=True, forcedExecution=True,
        sourceRevision='abc', operatingSystem='Linux', javaVersion='21', workerMode=mode, elapsedSeconds=duration,
        groups=[dict(name=group, suites=[dict(name=group + '.Test', cases=[
            dict(className=group + '.Test', name='contract', outcome='passed')])])
            for group in ('common', 'notification-provider', 'phone')])


class MetricsTest(unittest.TestCase):
    def test_equal_case_inventory_can_report_a_slowdown(self):
        result = compare([report('isolated'), report('reuse', 120)])
        self.assertEqual(result['testCases'], 3)
        self.assertEqual(result['elapsedReductionPercent'], -20)

    def test_missing_test_or_changed_outcome_cannot_claim_savings(self):
        for operation in ('remove', 'skip', 'duplicate'):
            changed = report('reuse', 1)
            cases = changed['groups'][0]['suites'][0]['cases']
            if operation == 'remove': cases.clear()
            elif operation == 'skip': cases[0]['outcome'] = 'skipped'
            else: cases.append(copy.deepcopy(cases[0]))
            with self.assertRaises(ValueError): compare([report('isolated'), changed])

    def test_incomplete_cached_failed_or_different_revision_is_rejected(self):
        for field, value in [('completed', False), ('forcedExecution', False), ('sourceDirty', True),
                             ('fullSuite', False), ('sourceRevision', 'other'),
                             ('operatingSystem', 'Windows'), ('elapsedSeconds', float('nan'))]:
            changed = report('reuse'); changed[field] = value
            with self.assertRaises(ValueError): compare([report('isolated'), changed])
        changed = report('reuse'); changed['groups'][0]['suites'][0]['cases'][0]['outcome'] = 'failed'
        with self.assertRaises(ValueError): compare([report('isolated'), changed])

    def test_cache_comparison_rejects_two_identically_dirty_checkouts(self):
        cold, warm = report('isolated'), report('isolated', 90)
        cold['sourceDirty'] = warm['sourceDirty'] = True
        with self.assertRaises(ValueError): compare_cache([cold, warm])

    def test_offline_corpus_is_named_and_cannot_be_compared_with_unfiltered(self):
        isolated, reuse = report('isolated'), report('reuse', 90)
        isolated['suiteScope'] = reuse['suiteScope'] = 'offline'
        self.assertEqual(compare([isolated, reuse])['scope'], 'offline')
        reuse['suiteScope'] = 'unfiltered'
        with self.assertRaises(ValueError): compare([isolated, reuse])
        reuse['suiteScope'] = 'required'
        with self.assertRaises(ValueError): compare([isolated, reuse])
        reuse['suiteScope'] = 'unknown'
        with self.assertRaises(ValueError): compare([isolated, reuse])

    def test_parallel_runner_usage_is_summed_but_feedback_time_is_not(self):
        jobs = [dict(conclusion='success', labels=['ubuntu-24.04'],
                     started_at='2026-11-16T00:00:10Z', completed_at='2026-11-16T00:01:10Z') for _ in range(2)]
        result = baseline([dict(run=dict(id=1, head_sha='abc', conclusion='success',
            created_at='2026-11-16T00:00:00Z'), jobs=jobs)])['runs'][0]
        self.assertEqual(result['feedbackSeconds'], 70)
        self.assertEqual(result['runnerSeconds'], 120)
        self.assertEqual(result['roundedRunnerMinutes'], 2)

    def test_failed_runs_remain_in_ledger_but_not_success_median(self):
        records = [dict(run=dict(id=1, head_sha='abc', conclusion='failure', created_at='2026-11-16T00:00:00Z'),
            jobs=[dict(conclusion='failure', labels=['ubuntu-24.04'],
                started_at='2026-11-16T00:00:00Z', completed_at='2026-11-16T00:01:00Z')])]
        result = baseline(records)
        self.assertEqual(len(result['runs']), 1)
        self.assertEqual(result['successfulUbuntu']['count'], 0)

    def test_cache_comparison_keeps_required_scope_explicit(self):
        cold, warm = report('isolated'), report('isolated', 90)
        cold['fullSuite'] = warm['fullSuite'] = False
        result = compare_cache([cold, warm])
        self.assertEqual(result['scope'], 'required')
        self.assertEqual(result['testExecutionSeconds'], [100, 90])
        warm['javaVersion'] = '22'
        with self.assertRaises(ValueError): compare_cache([cold, warm])


if __name__ == '__main__':
    unittest.main()
