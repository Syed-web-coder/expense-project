from __future__ import annotations

import hashlib
import json
from typing import Any

import numpy as np
import redis as redis_module
from langsmith import traceable
from numpy.typing import NDArray

SEMANTIC_THRESHOLD = 0.05

_EPOCH_PREFIX = "expense_ai:cache-epoch:"
_SEM_PREFIX = "expense_ai:sem:"


def _bucket_key(
    query_vec: NDArray[np.float32],
    tenant_id: str,
    epoch: int,
) -> str:
    vec_bytes = np.round(query_vec * 100).astype(np.int32).tobytes()
    epoch_bytes = epoch.to_bytes(8, byteorder="big")
    h = hashlib.sha256(vec_bytes + epoch_bytes).hexdigest()[:16]
    return f"{_SEM_PREFIX}{tenant_id}:{h}"


def get_epoch(r: redis_module.Redis, tenant_id: str) -> int:
    val = r.get(f"{_EPOCH_PREFIX}{tenant_id}")
    if val is None:
        return 0
    return int(val)


def bump_epoch(r: redis_module.Redis, tenant_id: str) -> int:
    return r.incr(f"{_EPOCH_PREFIX}{tenant_id}")


@traceable(run_type="chain", name="expense_ai.cache_lookup")
def cache_lookup(
    r: redis_module.Redis,
    query_vec: NDArray[np.float32],
    tenant_id: str,
) -> dict[str, object] | None:
    epoch = get_epoch(r, tenant_id)
    key = _bucket_key(query_vec, tenant_id, epoch)
    raw = r.get(key)
    if raw is None:
        return None
    try:
        answer: dict[str, Any] = json.loads(raw)
    except (json.JSONDecodeError, ValueError):
        return None
    citations = answer.get("citations", [])
    if not isinstance(citations, list):
        return None
    for citation in citations:
        if not isinstance(citation, dict):
            return None
        if citation.get("tenant_id") != tenant_id:
            return None  # defence-in-depth: cross-tenant citation → treat as miss
    return answer


def cache_store(
    r: redis_module.Redis,
    query_vec: NDArray[np.float32],
    tenant_id: str,
    answer: dict[str, object],
    ttl_seconds: int = 3600,
) -> None:
    epoch = get_epoch(r, tenant_id)
    key = _bucket_key(query_vec, tenant_id, epoch)
    r.set(key, json.dumps(answer), ex=ttl_seconds)
