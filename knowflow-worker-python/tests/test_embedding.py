import unittest

from app import embedding
from app.types import DocumentChunk


class MockEmbeddingTest(unittest.TestCase):
    def setUp(self):
        self.original_dim = embedding.config.embedding_dim
        embedding.config.embedding_dim = 4
        if hasattr(embedding, "_mock_warning_logged"):
            embedding._mock_warning_logged = False

    def tearDown(self):
        embedding.config.embedding_dim = self.original_dim
        if hasattr(embedding, "_mock_warning_logged"):
            embedding._mock_warning_logged = False

    def test_mock_embedding_warns_only_once(self):
        first = [DocumentChunk(document_id=1, kb_id=2, chunk_index=0, content="alpha")]
        second = [DocumentChunk(document_id=1, kb_id=2, chunk_index=1, content="beta")]

        with self.assertLogs("app.embedding", level="WARNING") as logs:
            embedding._mock_embed(first)
            embedding._mock_embed(second)

        self.assertEqual(1, len(logs.output))
        self.assertIn("MOCK embedding", logs.output[0])
        self.assertEqual(4, len(first[0].embedding))
        self.assertEqual(4, len(second[0].embedding))


if __name__ == "__main__":
    unittest.main()
