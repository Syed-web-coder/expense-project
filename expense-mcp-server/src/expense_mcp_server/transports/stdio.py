# expense-mcp-server/src/expense_mcp_server/transports/stdio.py
"""stdio transport entry point (Claude Desktop launches this via uvx).
Importing the tool/resource modules registers their decorators on the
module-level `mcp` instance before we call mcp.run().
"""
from __future__ import annotations

from expense_mcp_server.app import mcp
from expense_mcp_server.tools import _resources, llm, orders, rag  # noqa: F401  (registers decorators)


def main() -> None:
    mcp.run(transport="stdio")


if __name__ == "__main__":
    main()
