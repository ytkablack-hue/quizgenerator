import json
import threading
import unittest
from http.client import HTTPConnection
from urllib.error import HTTPError

from testhelper_server import ApiError, Gateway, MAX_BODY, make_server


def envelope(data):
    return {"status": "completed", "output": [{"type": "message", "content": [
        {"type": "output_text", "text": json.dumps(data, ensure_ascii=False)}
    ]}]}


class ApiTest(unittest.TestCase):
    def start_api(self, gateway, access_token=""):
        self.server = make_server(0, gateway, access_token)
        self.thread = threading.Thread(target=self.server.serve_forever, kwargs={"poll_interval": 0.01})
        self.thread.start()
        self.addCleanup(self.server.server_close)
        self.addCleanup(self.thread.join, 2)
        self.addCleanup(self.server.shutdown)

    def call(self, path, data=None, headers=None):
        connection = HTTPConnection("127.0.0.1", self.server.server_port, timeout=5)
        try:
            body = json.dumps(data).encode() if data is not None else None
            connection.request("POST" if body is not None else "GET", "/api/v1/" + path, body, headers or {})
            response = connection.getresponse()
            return response.status, json.load(response)
        finally:
            connection.close()

    def test_health_and_missing_provider_key(self):
        self.start_api(Gateway())
        self.assertEqual(self.call("health"), (200, {"status": "ok"}))
        self.assertEqual(self.call("ai/status"), (200, {"configured": False}))
        self.assertEqual(self.call("ai/quizzes", {"topic": "Физика", "count": 1})[0], 503)

    def test_bad_inputs_and_large_requests(self):
        self.start_api(Gateway())
        for payload in ({}, {"topic": "Физика", "count": False}, {"topic": "Физика", "count": 26}):
            self.assertEqual(self.call("ai/quizzes", payload)[0], 422)
        self.assertEqual(self.call("ai/flashcards", {"material": "x" * MAX_BODY})[0], 413)


    def test_authenticated_http_generation_with_mock_provider(self):
        def fake(*_args):
            return envelope({"title": "Физика", "questions": [{
                "text": "Единица тока?", "options": ["Ампер", "Вольт"], "correctIndex": 0,
                "explanation": "Ток измеряется в амперах.", "sourceExcerpt": None,
            }]})

        self.start_api(Gateway("test-only", transport=fake), "local-test")
        request = {"topic": "Физика", "count": 1}
        self.assertEqual(self.call("ai/quizzes", request)[0], 401)
        headers = {"Authorization": "Bearer local-test"}
        self.assertEqual(self.call("ai/status", headers=headers), (200, {"configured": True}))
        status, result = self.call("ai/quizzes", request, headers)
        self.assertEqual(status, 200)
        self.assertEqual(result["source"], "ai")
        self.assertEqual(result["questions"][0]["options"][0], "Ампер")


class GatewayTest(unittest.TestCase):
    def test_structured_quiz_and_only_verified_source_excerpt(self):
        sent = []

        def fake(payload, test_key):
            sent.append((payload, test_key))
            return envelope({"title": "Физика", "questions": [{
                "text": "Единица тока?", "options": ["Ампер", "Вольт"], "correctIndex": 0,
                "explanation": "Единица тока — ампер.", "sourceExcerpt": "Ток измеряется в амперах",
            }]})

        gateway = Gateway("test-only", transport=fake)
        result = gateway.generate("quizzes", {"material": "Ток измеряется в амперах.", "count": 1})
        self.assertEqual(result["source"], "ai")
        self.assertEqual(result["questions"][0]["sourceExcerpt"], "Ток измеряется в амперах")
        self.assertIsNone(gateway.generate("quizzes", {"topic": "Физика", "count": 1})["questions"][0]["sourceExcerpt"])
        self.assertTrue(sent[0][0]["text"]["format"]["strict"])
        self.assertFalse(sent[0][0]["store"])

    def test_cards_and_review(self):
        cards = Gateway("test-only", transport=lambda *args: envelope({"cards": [{"front": "Вопрос", "back": "Ответ"}]}))
        self.assertEqual(cards.generate("flashcards", {"topic": "Тема", "count": 1})["cards"][0]["back"], "Ответ")
        review = Gateway("test-only", transport=lambda *args: envelope({"summary": "Ток", "nextSteps": ["Повторить СИ"]}))
        self.assertEqual(review.generate("review", {"material": "Перепутал ампер и вольт"})["nextSteps"], ["Повторить СИ"])

    def test_rejects_partial_invalid_or_refused_provider_data(self):
        outputs = [
            {"status": "incomplete", "output": []},
            envelope({"title": "Тест", "questions": []}),
            envelope({"title": "Тест", "questions": [{"text": "Q", "options": ["A", "B"],
                        "correctIndex": 2, "explanation": "x", "sourceExcerpt": None}]}),
            {"status": "completed", "output": [{"type": "message", "content": [{"type": "refusal"}]}]},
        ]
        for result in outputs:
            with self.subTest(result=result), self.assertRaisesRegex(ApiError, "некорректный"):
                Gateway("test-only", transport=lambda *_: result).generate("quizzes", {"topic": "Q", "count": 1})

    def test_timeout_and_rate_limit_are_reported(self):
        for error, expected in ((TimeoutError(), 504), (HTTPError("", 429, "rate limit", {}, None), 503)):
            def failing(*_):
                raise error
            with self.assertRaises(ApiError) as caught:
                Gateway("test-only", transport=failing).generate("quizzes", {"topic": "Q", "count": 1})
            self.assertEqual(caught.exception.status, expected)


if __name__ == "__main__":
    unittest.main()
