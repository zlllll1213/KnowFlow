import threading
import time
import unittest
from concurrent.futures import ThreadPoolExecutor
from unittest.mock import patch

from app import repository


class RepositoryPoolTest(unittest.TestCase):
    def setUp(self):
        self.original_pool = repository._connection_pool
        self.original_dsn = repository.config.db_dsn_override
        repository._connection_pool = None
        repository.config.db_dsn_override = "postgresql://user:pass@db:5432/knowflow"

    def tearDown(self):
        repository._connection_pool = self.original_pool
        repository.config.db_dsn_override = self.original_dsn

    def test_init_pool_creates_connection_pool_once_across_threads(self):
        calls = []
        calls_lock = threading.Lock()

        class FakePool:
            def __init__(self, minconn, maxconn, dsn):
                # 放大并发窗口，防止测试只覆盖串行初始化路径。
                time.sleep(0.01)
                with calls_lock:
                    calls.append((minconn, maxconn, dsn))

        with patch.object(repository.pool, "ThreadedConnectionPool", FakePool):
            with ThreadPoolExecutor(max_workers=8) as executor:
                list(executor.map(lambda _: repository.init_pool(2, 6), range(16)))

        self.assertEqual(1, len(calls))
        self.assertEqual((2, 6, "postgresql://user:pass@db:5432/knowflow"), calls[0])
        self.assertIsInstance(repository._connection_pool, FakePool)


if __name__ == "__main__":
    unittest.main()
