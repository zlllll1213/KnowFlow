import importlib
import os
import unittest
from unittest.mock import patch


class ConfigTest(unittest.TestCase):
    def test_db_max_connections_reads_environment(self):
        with patch.dict(os.environ, {"WORKER_DB_MAX_CONNECTIONS": "6"}, clear=False):
            import app.config as config_module
            importlib.reload(config_module)

            self.assertEqual(6, config_module.Config().db_max_connections)

    def test_validate_rejects_non_positive_db_max_connections(self):
        import app.config as config_module

        cfg = config_module.Config()
        cfg.db_password = "secret"
        cfg.db_max_connections = 0

        self.assertIn("WORKER_DB_MAX_CONNECTIONS 必须大于 0", cfg.validate())


if __name__ == "__main__":
    unittest.main()
