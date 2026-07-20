# expense-mcp-server/healthcheck.py
"""Docker HEALTHCHECK script. Hits /sse and treats ANY HTTP response
(including 401 -- the endpoint requires a bearer JWT we don't have here)
as healthy: this confirms the process is alive and serving HTTP, not
that a specific business outcome occurs. Only connection failures or
timeouts should fail the healthcheck.
"""
import sys
import urllib.error
import urllib.request

try:
    urllib.request.urlopen("http://localhost:8080/sse", timeout=3)
except urllib.error.HTTPError:
    # Got a real HTTP response (e.g. 401 unauthorized) -- server is alive.
    sys.exit(0)
except (urllib.error.URLError, TimeoutError):
    sys.exit(1)
sys.exit(0)
