"""OCR upload guard: the pixel budget is judged from the header, before any pixel is decoded.

A 236 KB PNG can declare 81 megapixels and cost ~600 MB to decode. These tests use header-only
PNGs (45-68 bytes) so the guard is exercised without ever allocating a large raster.
"""

import io
import struct
import zlib

import pytest
from fastapi.testclient import TestClient
from PIL import Image, ImageFile

from finunity_server.config import Settings
from finunity_server.main import create_app
from finunity_server.models import ModelRecognitionResult, RecognizedHolding
from finunity_server.v1_api import ApiProblem, check_image


def _chunk(kind: bytes, data: bytes) -> bytes:
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))


def header_only_png(width: int, height: int, *, truncated_data: bool = False) -> bytes:
    """A syntactically valid PNG header that declares width x height but carries no pixels."""
    body = b"\x89PNG\r\n\x1a\n" + _chunk(
        b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    )
    if truncated_data:
        body += _chunk(b"IDAT", zlib.compress(b"\x00" * 10))
    return body + _chunk(b"IEND", b"")


def real_image(size=(600, 600), fmt="PNG") -> bytes:
    out = io.BytesIO()
    Image.new("RGB", size, "white").save(out, format=fmt)
    return out.getvalue()


class FakeRecognizer:
    calls = 0

    async def recognize(self, **kwargs):
        self.calls += 1
        return ModelRecognitionResult(
            holdings=[
                RecognizedHolding(
                    name="Apple",
                    security_code="AAPL",
                    asset_type="STOCK",
                    quantity=2,
                    current_price=10,
                    market_value=20,
                    currency="USD",
                    confidence=0.9,
                    cost_source="NOT_SHOWN",
                    needs_review=True,
                )
            ]
        )


def make_client(**overrides):
    app = create_app(Settings(database_path=":memory:", ocr_enabled=True, **overrides))
    app.state.recognizer = FakeRecognizer()
    return app, TestClient(app)


def session_headers(client):
    data = client.post(
        "/api/v1/auth/session",
        json={
            "installation_id": "installation-guard-1",
            "platform": "ANDROID",
            "app_version": "1.0.0",
            "version_code": 1,
        },
    ).json()["data"]
    return {"Authorization": f"Bearer {data['access_token']}"}


def upload(client, headers, content, content_type="image/png", request_id="guard-1"):
    return client.post(
        "/api/v1/ocr/holdings:parse",
        headers=headers,
        files={"image": ("holding.png", content, content_type)},
        data={"client_request_id": request_id},
    )


def error_code(response) -> str:
    return response.json()["error"]["code"]


def test_oversized_dimensions_are_rejected_before_any_pixel_is_decoded(monkeypatch):
    def decode_forbidden(self):
        raise AssertionError("pixels were decoded before the pixel budget was checked")

    app, client = make_client(ocr_installation_daily_limit=1)
    with client:
        headers = session_headers(client)
        monkeypatch.setattr(ImageFile.ImageFile, "load", decode_forbidden)
        response = upload(client, headers, header_only_png(9000, 9000))  # 81 MP > 20 MP limit
        assert response.status_code == 413 and error_code(response) == "IMAGE_TOO_LARGE"
        monkeypatch.undo()
        # The rejected upload must not have spent the OCR budget or reached the model.
        assert app.state.recognizer.calls == 0
        assert upload(client, headers, real_image(), request_id="guard-2").status_code == 200


def test_decompression_bomb_error_is_a_413_not_a_500():
    _, client = make_client()
    with client:
        response = upload(client, session_headers(client), header_only_png(14000, 14000))
        assert response.status_code == 413 and error_code(response) == "IMAGE_TOO_LARGE"


@pytest.mark.parametrize(
    ("content", "expected_status", "expected_code"),
    [
        (b"this is not an image", 400, "INVALID_IMAGE"),
        (header_only_png(600, 600, truncated_data=True), 400, "INVALID_IMAGE"),  # corrupt body
        (real_image(fmt="JPEG"), 400, "IMAGE_TYPE_MISMATCH"),  # JPEG sent as image/png
        (real_image(size=(100, 100)), 422, "IMAGE_TOO_SMALL"),
    ],
)
def test_bad_uploads_get_precise_client_errors(content, expected_status, expected_code):
    _, client = make_client()
    with client:
        response = upload(client, session_headers(client), content)
        assert response.status_code == expected_status
        assert error_code(response) == expected_code


def test_valid_image_is_decoded_exactly_once(monkeypatch):
    calls = []
    original = ImageFile.ImageFile.load

    def counting_load(self):
        calls.append(self.size)
        return original(self)

    monkeypatch.setattr(ImageFile.ImageFile, "load", counting_load)
    app, client = make_client()
    with client:
        assert upload(client, session_headers(client), real_image()).status_code == 200
    assert calls == [(600, 600)]  # the old exif_transpose path decoded and copied the raster


def test_check_image_enforces_the_configured_pixel_budget():
    settings = Settings(database_path=":memory:", max_image_megapixels=1)
    check_image(real_image(size=(600, 600)), "image/png", settings)  # 0.36 MP is fine
    with pytest.raises(ApiProblem) as raised:
        check_image(real_image(size=(1200, 1200)), "image/png", settings)  # 1.44 MP
    assert raised.value.http_status == 413 and raised.value.code == "IMAGE_TOO_LARGE"
