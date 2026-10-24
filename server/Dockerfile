FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

WORKDIR /app
ARG INSTALL_PADDLEOCR=false

RUN if [ "$INSTALL_PADDLEOCR" = "true" ]; then \
      apt-get update && apt-get install --no-install-recommends -y libgomp1 libgl1 libglib2.0-0 \
      && rm -rf /var/lib/apt/lists/*; fi

COPY pyproject.toml README.md ./
COPY src ./src

RUN pip install --no-cache-dir . \
    && if [ "$INSTALL_PADDLEOCR" = "true" ]; then \
      pip install --no-cache-dir ".[paddleocr]" \
      && pip install --no-cache-dir --no-deps "paddlepaddle==3.4.0" -i https://www.paddlepaddle.org.cn/packages/stable/cpu/ \
      && pip install --no-cache-dir "opt_einsum==3.3.0" "protobuf>=3.20.2" "networkx" "typing_extensions" "safetensors>=0.6.0"; fi \
    && useradd --create-home --uid 10001 appuser \
    && chown -R appuser:appuser /app

USER appuser

# Build identity for GET /health/version; passed with --build-arg (see docker-compose.yml).
ARG GIT_SHA=unknown
ARG BUILD_TIME=unknown
ENV GIT_SHA=$GIT_SHA BUILD_TIME=$BUILD_TIME

EXPOSE 8000

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8000/health/live', timeout=3)"

CMD ["uvicorn", "finunity_server.main:app", "--host", "0.0.0.0", "--port", "8000", "--proxy-headers", "--forwarded-allow-ips", "127.0.0.1"]
