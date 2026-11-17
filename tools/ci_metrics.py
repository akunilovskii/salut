"""Compare execution evidence, keeping elapsed time separate from runner usage."""
import argparse
from collections import Counter
from datetime import datetime
import json
import math
from pathlib import Path
from statistics import median


def seconds(start, end):
    return (datetime.fromisoformat(end.replace('Z', '+00:00'))
            - datetime.fromisoformat(start.replace('Z', '+00:00'))).total_seconds()


def baseline(records):
    rows = []
    for record in records:
        run = record['run']
        jobs = [job for job in record['jobs'] if job['conclusion'] != 'skipped']
        if not jobs or any(not job.get('completed_at') for job in jobs):
            continue
        durations = [seconds(job['started_at'], job['completed_at']) for job in jobs]
        rows.append(dict(runId=run['id'], revision=run['head_sha'], conclusion=run['conclusion'],
            ubuntu=all('ubuntu-24.04' in job.get('labels', []) for job in jobs),
            feedbackSeconds=max(seconds(run['created_at'], job['completed_at']) for job in jobs),
            runnerSeconds=sum(durations), roundedRunnerMinutes=sum(math.ceil(value / 60) for value in durations)))
    cohort = [row for row in rows if row['ubuntu'] and row['conclusion'] == 'success']
    return dict(runs=rows, successfulUbuntu=dict(count=len(cohort),
        medianFeedbackSeconds=median(row['feedbackSeconds'] for row in cohort) if cohort else None,
        medianRunnerSeconds=median(row['runnerSeconds'] for row in cohort) if cohort else None),
        limits='Observed, changing workloads. Runner minutes are consumption proxies, not an invoice; storage is excluded.')


def scope(report):
    value = report.get('suiteScope', 'unfiltered' if report.get('fullSuite') else 'required')
    if value not in ('required', 'unfiltered', 'offline'):
        raise ValueError('Unknown corpus scope')
    if (value == 'required') == bool(report.get('fullSuite')):
        raise ValueError('Corpus scope disagrees with fullSuite')
    return value


def inventory(report, require_full=True):
    if report.get('schemaVersion') != 1 or not report.get('completed'):
        raise ValueError('Incomplete execution report')
    if (require_full and not report.get('fullSuite')) or not report.get('forcedExecution'):
        raise ValueError('Comparison requires fresh, complete corpus execution')
    scope(report)
    result = Counter()
    groups = set()
    for group in report['groups']:
        if group['name'] in groups:
            raise ValueError('Duplicate test group')
        groups.add(group['name'])
        if not group['suites'] or any(not suite['cases'] for suite in group['suites']):
            raise ValueError('A full-suite group or suite is empty')
        for suite in group['suites']:
            for case in suite['cases']:
                if case['outcome'] not in ('passed', 'skipped'):
                    raise ValueError('A test failed')
                result[(group['name'], suite['name'], case['className'], case['name'], case['outcome'])] += 1
    if groups != {'common', 'notification-provider', 'phone'} or not result:
        raise ValueError('Missing full-suite groups or testcases')
    return result


def compare(reports):
    if len(reports) < 2:
        raise ValueError('At least two execution reports are required')
    reference = reports[0]
    if not reference.get('sourceRevision'):
        raise ValueError('A named source revision is required')
    expected = inventory(reference)
    modes = {}
    for report in reports:
        if (report.get('sourceRevision') != reference['sourceRevision']
                or report.get('operatingSystem') != reference['operatingSystem']
                or report.get('javaVersion') != reference.get('javaVersion')
                or scope(report) != scope(reference)):
            raise ValueError('Source revision, platform, Java or corpus scope changed')
        if inventory(report) != expected:
            raise ValueError('Test identities, counts or outcomes changed')
        duration = report['elapsedSeconds']
        if not isinstance(duration, (int, float)) or not math.isfinite(duration) or duration <= 0:
            raise ValueError('Invalid elapsed time')
        modes.setdefault(report['workerMode'], []).append(duration)
    if set(modes) != {'isolated', 'reuse'}:
        raise ValueError('Both isolated and reuse modes are required')
    before, after = (median(modes[key]) for key in ('isolated', 'reuse'))
    return dict(sourceRevision=reference['sourceRevision'], inventoryMatches=True,
        scope=scope(reference), testCases=sum(expected.values()), skippedCases=sum(n for key, n in expected.items() if key[-1] == 'skipped'),
        samples=modes, isolatedMedianSeconds=before, reuseMedianSeconds=after,
        elapsedReductionPercent=100 * (before - after) / before,
        limits='Same-host forced full-suite timings after warmup; not whole-workflow time, billing or regression recall.')


def compare_cache(reports):
    if len(reports) != 2 or not reports[0].get('sourceRevision'):
        raise ValueError('Two named-revision cache qualification reports are required')
    before, after = reports
    for field in ('sourceRevision', 'operatingSystem', 'javaVersion', 'workerMode', 'fullSuite'):
        if before.get(field) != after.get(field):
            raise ValueError('Cache comparison changed ' + field)
    if scope(before) != scope(after):
        raise ValueError('Cache comparison changed corpus scope')
    expected = inventory(before, require_full=False)
    if inventory(after, require_full=False) != expected:
        raise ValueError('Cache comparison changed test identities, counts or outcomes')
    return dict(sourceRevision=before['sourceRevision'], inventoryMatches=True,
        scope=scope(before), testCases=sum(expected.values()),
        testExecutionSeconds=[before['elapsedSeconds'], after['elapsedSeconds']],
        limits='Cache restore/save and queue time are excluded here. Compare whole-job API timestamps before claiming feedback or compute savings.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('operation', choices=['baseline', 'compare', 'compare-cache'])
    parser.add_argument('inputs', nargs='+', type=Path)
    parser.add_argument('--out', type=Path)
    args = parser.parse_args()
    values = [json.loads(path.read_text(encoding='utf-8-sig')) for path in args.inputs]
    if args.operation == 'baseline' and len(values) != 1:
        parser.error('baseline expects one complete ledger')
    try:
        operation = {'baseline': lambda: baseline(values[0]), 'compare': lambda: compare(values),
                     'compare-cache': lambda: compare_cache(values)}[args.operation]
        value = operation()
    except (KeyError, TypeError, ValueError) as error:
        parser.exit(2, str(error) + '\n')
    text = json.dumps(value, indent=2) + '\n'
    if args.out:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(text, encoding='utf-8')
    else:
        print(text, end='')


if __name__ == '__main__':
    main()
