import unittest

from app import main


class MainSecurityTest(unittest.TestCase):
    def test_database_pool_max_connections_uses_configured_cap(self):
        original_concurrency = main.config.concurrency
        original_max = main.config.db_max_connections
        try:
            main.config.concurrency = 50
            main.config.db_max_connections = 7

            self.assertEqual(7, main.database_pool_max_connections())
        finally:
            main.config.concurrency = original_concurrency
            main.config.db_max_connections = original_max

    def test_redact_url_hides_passwords(self):
        self.assertEqual(
            "postgresql://user:***@db:5432/knowflow",
            main.redact_url("postgresql://user:secret@db:5432/knowflow"),
        )
        self.assertEqual(
            "redis://:***@redis:6379/0",
            main.redact_url("redis://:redis-secret@redis:6379/0"),
        )


if __name__ == "__main__":
    unittest.main()
