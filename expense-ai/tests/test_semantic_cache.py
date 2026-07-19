from __future__ import annotations

import json
import os
from collections.abc import Generator
from typing import Any

import numpy as np
import pytest
import redis as redis_module
from numpy.typing import NDArray
from testcontainers.redis import RedisContainer  # type: ignore[import-untyped]

from expense_ai.cache import (
    _bucket_key,
    bump_epoch,
    cache_lookup,
    cache_store,
    get_epoch,
)

os.environ.setdefault("LANGSMITH_API_KEY", "test-dummy-langsmith-key")
os.environ["LANGSMITH_TRACING"] = "false"

_REDIS_IMAGE = "redis:7"
_DIM = 384


@pytest.fixture(scope="module")
def redis_client() -> Generator[redis_module.Redis, None, None]:
    with RedisContainer(_REDIS_IMAGE) as container:
        client: redis_module.Redis = container.get_client()
        yield client


def _vec(seed: int = 0) -> NDArray[np.float32]:
    rng = np.random.default_rng(seed)
    v = rng.random(_DIM).astype(np.float32)
    return (v / (np.linalg.norm(v) + 1e-10)).astype(np.float32)


# ---------------------------------------------------------------------------
# Test (i) — near-duplicate vectors (differ by <0.001 per component) → same key
# ---------------------------------------------------------------------------
def test_near_duplicate_vectors_share_bucket(redis_client: redis_module.Redis) -> None:
    # Craft vectors that differ by <0.001 and map to identical rounded-*100 int32 bytes.
    # vec1[0]=1.0 → 100; vec2 adds 0.0009 to a zero component: 0.0009*100=0.09 → rounds to 0.
    # The rounded int32 representation is identical for both, so the sha256 key matches.
    vec1 = np.zeros(_DIM, dtype=np.float32)
    vec1[0] = 1.0
    vec2 = vec1.copy()
    vec2[1] = np.float32(0.0009)  # diff < 0.001; scaled 0.09 rounds to 0 — same bucket

    key1 = _bucket_key(vec1, "tenant-a", 0)
    key2 = _bucket_key(vec2, "tenant-a", 0)
    assert key1 == key2, "Vectors differing by <0.001 must hash to the same bucket key"


# ---------------------------------------------------------------------------
# Test (ii) — same vector under tenant-b misses tenant-a's cached entry
# ---------------------------------------------------------------------------
def test_tenant_isolation_in_cache(redis_client: redis_module.Redis) -> None:
    vec = _vec(2)
    answer: dict[str, object] = {
        "text": "deductible meal",
        "citations": [{"doc_id": "d-1", "tenant_id": "tenant-a"}],
        "rerank_timed_out": False,
    }
    cache_store(redis_client, vec, "tenant-a", answer, ttl_seconds=300)

    # Hit for tenant-a
    result_a = cache_lookup(redis_client, vec, "tenant-a")
    assert result_a is not None

    # Miss for tenant-b (different key due to tenant_id in hash)
    result_b = cache_lookup(redis_client, vec, "tenant-b")
    assert result_b is None


# ---------------------------------------------------------------------------
# Test (iii) — bump_epoch makes the prior cached entry unreachable
# ---------------------------------------------------------------------------
def test_bump_epoch_invalidates_cache(redis_client: redis_module.Redis) -> None:
    vec = _vec(3)
    answer: dict[str, object] = {
        "text": "travel deduction",
        "citations": [{"doc_id": "d-2", "tenant_id": "tenant-epoch-test"}],
        "rerank_timed_out": False,
    }
    cache_store(redis_client, vec, "tenant-epoch-test", answer, ttl_seconds=300)
    assert cache_lookup(redis_client, vec, "tenant-epoch-test") is not None

    bump_epoch(redis_client, "tenant-epoch-test")

    # After epoch bump the key changes → cache miss
    assert cache_lookup(redis_client, vec, "tenant-epoch-test") is None


# ---------------------------------------------------------------------------
# Defence-in-depth — citation with wrong tenant_id → treated as miss
# ---------------------------------------------------------------------------
def test_cross_tenant_citation_returns_none(redis_client: redis_module.Redis) -> None:
    vec = _vec(4)
    bad_answer: dict[str, Any] = {
        "text": "cross-tenant leak",
        "citations": [
            {"doc_id": "evil-doc", "tenant_id": "tenant-b"}  # wrong tenant
        ],
        "rerank_timed_out": False,
    }
    # Manually write with the correct epoch so lookup finds the key
    epoch = get_epoch(redis_client, "tenant-defence-test")
    key = _bucket_key(vec, "tenant-defence-test", epoch)
    redis_client.set(key, json.dumps(bad_answer), ex=300)

    result = cache_lookup(redis_client, vec, "tenant-defence-test")
    assert result is None, "Citation with wrong tenant_id must be treated as a cache miss"
