import sys

# psycopg async requires SelectorEventLoop; Windows defaults to ProactorEventLoop.
# Set the policy here so any import of this package locks in the right policy
# before any event loop is created — covers pytest, manual asyncio.run() callers,
# and anything that respects the policy (uvicorn with loop="none" among others).
if sys.platform == "win32":
    import asyncio

    asyncio.set_event_loop_policy(asyncio.WindowsSelectorEventLoopPolicy())
