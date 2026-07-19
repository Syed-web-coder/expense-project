// loadtests/expense-api-p99.js
// TASK 3 (W6D5): k6 script with options.thresholds mapping exactly
// to the W5D5 SLO numbers; exit code becomes the CI gate.
//
// KNOWN BLOCKER: this cannot actually run successfully yet.
// expense-api is not deployed as a running Deployment anywhere in
// this cluster (confirmed in W6D5 Task 2 -- only an unpullable
// worker stub exists). TARGET defaults to the real dev namespace
// (not the reference's nonexistent "staging" one), but there is
// currently nothing listening at that address. Written correctly
// and ready to run once a real expense-api Deployment exists.

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter } from 'k6/metrics';

// Custom Trend reads the W6D4 X-Cost-Usd response header the proxy
// emits. The threshold gates the W6D4 cost SLI alongside latency.
const costPerReq = new Trend('cost_per_request_usd');
const tenantSynth = new Counter('synth_calls_total');

export const options = {
  // 12-minute total: 4m ramp up + 6m hold + 2m down. The hold is
  // long enough for HPA / KEDA polling cycles (15s and 15s) to
  // settle and steady state to emerge.
  stages: [
    { duration: '4m', target: 200 },
    { duration: '6m', target: 200 },
    { duration: '2m', target: 0   },
  ],
  thresholds: {
    // p99 latency under 600 ms -- W5D5 SLO. Statistic is p(99) NOT
    // avg; average hides the tail and the tail is the whole point.
    http_req_duration:    ['p(99)<600'],
    // Error rate under 0.01 -- W5D5 SLO.
    http_req_failed:      ['rate<0.01'],
    // Functional correctness under load: smoke + load combined.
    checks:                ['rate>0.99'],
    // Cost SLI gate: p95 of $/request under 0.004 USD -- W6D4 cost
    // SLI target.
    cost_per_request_usd: ['p(95)<0.004'],
  },
};

// Defaults to the real dev namespace, not the reference's
// nonexistent "staging" one -- nothing is deployed under either
// name yet, but this at least points at a namespace that exists.
const BASE = __ENV.TARGET ||
  'http://expense-api.expense-dev.svc.cluster.local:8080';

// Workload mix weights MUST sum to 1.0.
const MIX = [
  { weight: 0.7, run: writeHotPath },
  { weight: 0.2, run: writeColdPath },
  { weight: 0.1, run: readPath     },
];

export default function () {
  const r = Math.random();
  let acc = 0;
  for (const step of MIX) {
    acc += step.weight;
    if (r <= acc) {
      step.run();
      break;
    }
  }
  sleep(0.5);
}

function writeHotPath() {
  // tenant-synth: the synthetic-tenant tag the W6D4 CostMiddleware
  // filter already recognises so finance can subtract the spike
  // from the real spend tally. NEVER a real tenant id in a load test.
  const res = http.post(`${BASE}/v1/merchants`,
    JSON.stringify({
      tenant:  'tenant-synth',
      feature: 'categorize-expense',
      size:    'small',
    }),
    { headers: {
      'Content-Type': 'application/json',
      'X-Tenant':     'tenant-synth',
      'X-Feature':    'categorize-expense',
    }});
  check(res, {
    'status 200': (r) => r.status === 200,
    'has merchantId': (r) => r.json('merchantId') !== undefined,
  });
  const cost = parseFloat(res.headers['X-Cost-Usd'] || '0');
  costPerReq.add(cost);
  tenantSynth.add(1);
}

function writeColdPath() {
  // Same endpoint, larger payload; exercises the higher-token-cost
  // path so the cost SLI gate has pressure from both directions.
  const res = http.post(`${BASE}/v1/merchants`,
    JSON.stringify({
      tenant:  'tenant-synth',
      feature: 'categorize-expense',
      size:    'large',
    }),
    { headers: {
      'Content-Type': 'application/json',
      'X-Tenant':     'tenant-synth',
      'X-Feature':    'categorize-expense',
    }});
  check(res, { 'status 200': (r) => r.status === 200 });
  const cost = parseFloat(res.headers['X-Cost-Usd'] || '0');
  costPerReq.add(cost);
  tenantSynth.add(1);
}

function readPath() {
  const res = http.get(
    `${BASE}/v1/merchants/00000000-0000-0000-0000-000000000001`);
  check(res, { 'status 200 or 404': (r) =>
    r.status === 200 || r.status === 404 });
}
