with open("backend/tests/conftest.py", "r", encoding="utf-8") as f:
    content = f.read()

content = content.replace("return app.test_client()", "return TestClient(app)")

with open("backend/tests/conftest.py", "w", encoding="utf-8") as f:
    f.write(content)

print("Файл backend/tests/conftest.py успешно обновлён!")
