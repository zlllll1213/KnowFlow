import sys
import threading
import types
import unittest
from concurrent.futures import ThreadPoolExecutor
from unittest.mock import patch

from app import main


class MinioClientSingletonTest(unittest.TestCase):
    def setUp(self):
        self.original_client = main._minio_client
        self.original_endpoint = main.config.minio_endpoint
        self.original_access_key = main.config.minio_access_key
        self.original_secret_key = main.config.minio_secret_key
        self.original_secure = main.config.minio_secure
        main._minio_client = None
        main.config.minio_endpoint = "minio:9000"
        main.config.minio_access_key = "access"
        main.config.minio_secret_key = "secret"
        main.config.minio_secure = True

    def tearDown(self):
        main._minio_client = self.original_client
        main.config.minio_endpoint = self.original_endpoint
        main.config.minio_access_key = self.original_access_key
        main.config.minio_secret_key = self.original_secret_key
        main.config.minio_secure = self.original_secure

    def test_get_minio_client_reuses_single_instance_across_threads(self):
        calls = []
        lock = threading.Lock()

        class FakeMinio:
            def __init__(self, endpoint, access_key, secret_key, secure):
                # 让并发调用同时争抢锁，避免测试只覆盖串行路径。
                with lock:
                    calls.append((endpoint, access_key, secret_key, secure))

        fake_minio_module = types.SimpleNamespace(Minio=FakeMinio)

        with patch.dict(sys.modules, {"minio": fake_minio_module}):
            with ThreadPoolExecutor(max_workers=8) as executor:
                clients = list(executor.map(lambda _: main._get_minio_client(), range(16)))

        self.assertEqual(1, len(calls))
        self.assertEqual(("minio:9000", "access", "secret", True), calls[0])
        self.assertTrue(all(client is clients[0] for client in clients))


if __name__ == "__main__":
    unittest.main()
