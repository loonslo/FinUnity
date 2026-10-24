from functools import lru_cache
from typing import Literal

from pydantic import Field, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        # Environment injection is explicit (Compose env_file / uv --env-file).
        env_file=None,
        env_file_encoding="utf-8",
        extra="ignore",
    )

    app_name: str = "FinUnity Server"
    app_env: Literal["development", "production"] = "development"
    email_auth_enabled: bool = False
    ocr_enabled: bool = False
    ocr_user_daily_limit: int = Field(default=20, gt=0)
    ocr_user_global_daily_limit: int = Field(default=200, gt=0)
    ocr_installation_daily_limit: int = Field(default=10, gt=0)
    ocr_ip_daily_limit: int = Field(default=30, gt=0)
    ocr_anonymous_global_daily_limit: int = Field(default=100, gt=0)
    cache_capacity: int = Field(default=1000, gt=0)
    cache_ttl_seconds: int = Field(default=86400, gt=0, le=86400)
    registration_hourly_limit: int = Field(default=5, gt=0)
    registration_daily_limit: int = Field(default=20, gt=0)
    registration_alert_threshold: int = Field(default=100, gt=0)
    auth_ip_minute_limit: int = Field(default=20, gt=0)
    auth_ip_failure_limit: int = Field(default=20, gt=0)
    # Anonymous session endpoints need no credentials, so they are throttled like registration.
    session_ip_hourly_limit: int = Field(default=60, gt=0)
    session_refresh_ip_hourly_limit: int = Field(default=300, gt=0)
    session_installation_hourly_limit: int = Field(default=10, gt=0)
    session_global_daily_limit: int = Field(default=20_000, gt=0)
    ocr_provider: Literal["qwen", "baidu", "openai", "paddleocr"] = "qwen"
    qwen_api_key: str | None = None
    qwen_base_url: str = "https://dashscope.aliyuncs.com/compatible-mode/v1"
    qwen_model: str = "qwen3.7-flash"
    # Vision requests can take over a minute for tall mobile screenshots. Do not
    # let the SDK retry the same expensive request silently after a timeout.
    qwen_timeout_seconds: float = Field(default=120.0, gt=0, le=300)
    qwen_max_retries: int = Field(default=0, ge=0, le=2)
    qwen_max_output_tokens: int = Field(default=1600, gt=0, le=8192)
    qwen_max_pixels: int = Field(default=2_621_440, gt=65_536, le=16_777_216)
    qwen_enable_thinking: bool = False
    baidu_ocr_api_key: str | None = None
    baidu_ocr_secret_key: str | None = None
    baidu_ocr_timeout_seconds: float = Field(default=30.0, gt=0, le=120)
    paddleocr_device: str = "cpu"
    paddleocr_lang: str = "ch"
    paddleocr_enable_mkldnn: bool = False
    paddleocr_use_doc_orientation_classify: bool = False
    paddleocr_use_doc_unwarping: bool = False
    paddleocr_use_textline_orientation: bool = False
    openai_api_key: str | None = None
    openai_base_url: str | None = None
    openai_model: str = "gpt-5-mini"
    openai_timeout_seconds: float = Field(default=60.0, gt=0, le=300)
    max_image_bytes: int = Field(default=10 * 1024 * 1024, gt=0, le=25 * 1024 * 1024)
    max_image_megapixels: int = Field(default=20, gt=0, le=100)
    minimum_image_dimension: int = Field(default=480, gt=0, le=4096)
    session_secret: str = "development-only-change-me"
    access_token_ttl_seconds: int = Field(default=3600, gt=0, le=86400)
    refresh_token_ttl_seconds: int = Field(default=30 * 86400, gt=0)
    minimum_supported_version_code: int = Field(default=1, gt=0)
    latest_version_code: int = Field(default=1, gt=0)
    privacy_policy_url: str = "https://finunity.halfopen.dev/privacy"
    maintenance: bool = False
    holding_prices_url: str | None = None
    holding_prices_token: str | None = None
    holding_prices_hour: int = Field(default=18, ge=0, le=23)
    database_path: str = "data/finunity.db"
    email_code_ttl_seconds: int = Field(default=600, gt=0, le=3600)
    email_code_pepper: str = "development-only-change-me"
    password_pepper: str = "development-only-change-me"
    password_max_attempts: int = Field(default=5, ge=1, le=20)
    password_lockout_seconds: int = Field(default=900, ge=1, le=86400)
    expose_verification_code: bool = False
    smtp_host: str | None = None
    smtp_port: int = Field(default=587, gt=0, le=65535)
    smtp_username: str | None = None
    smtp_password: str | None = None
    smtp_from_email: str | None = None
    smtp_use_tls: bool = True
    smtp_use_ssl: bool = False
    cors_origins: list[str] = ["http://127.0.0.1:5173", "http://localhost:5173"]

    @model_validator(mode="after")
    def validate_production(self) -> "Settings":
        if self.app_env != "production":
            return self
        errors = []
        for name in ("session_secret", "password_pepper"):
            value = getattr(self, name)
            if (
                len(value) < 32
                or value == "development-only-change-me"
                or value.startswith(("replace-with-", "change-this-"))
            ):
                errors.append(
                    f"{name.upper()} must be an independent secret of at least 32 characters"
                )
        if self.email_auth_enabled:
            if not (self.smtp_host and self.smtp_from_email):
                errors.append("EMAIL_AUTH_ENABLED requires SMTP_HOST and SMTP_FROM_EMAIL")
            if len(self.email_code_pepper) < 32 or self.email_code_pepper.startswith(
                ("development-", "replace-with-")
            ):
                errors.append("EMAIL_CODE_PEPPER must be a secret of at least 32 characters")
            if self.expose_verification_code:
                errors.append("EXPOSE_VERIFICATION_CODE must be false in production")
        if "example.com" in self.privacy_policy_url or not self.privacy_policy_url.startswith(
            "https://"
        ):
            errors.append("PRIVACY_POLICY_URL must be a public HTTPS policy URL")
        if errors:
            raise ValueError("Production configuration rejected: " + "; ".join(errors))
        return self


@lru_cache
def get_settings() -> Settings:
    return Settings()
