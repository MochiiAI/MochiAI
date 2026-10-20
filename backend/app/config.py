"""Application settings loaded from environment / .env file."""

from functools import lru_cache

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Typed settings for the Mochi cloud backend."""

    model_config = SettingsConfigDict(env_file=".env", env_prefix="MOCHI_", extra="ignore")

    # Environment toggle: dev -> SQLite default, prod -> Postgres expected.
    env: str = "dev"

    # Database -------------------------------------------------------------
    database_url: str = "sqlite:///./mochi.db"

    # Auth -----------------------------------------------------------------
    jwt_secret: str = "CHANGE_ME_please_use_a_long_random_secret"
    jwt_algorithm: str = "HS256"
    jwt_expire_days: int = 90

    # LLM providers used by the cloud task executor --------------------------
    openai_api_key: str | None = None
    openai_model: str = "gpt-4o"
    anthropic_api_key: str | None = None
    anthropic_model: str = "claude-3-5-sonnet-latest"

    # GitHub ----------------------------------------------------------------
    # Default bot token for the internal "MochiiAI" account. The Android app has
    # the same default baked into SecretConfig.kt; the server-side default lets
    # REST/WS callers operate without passing a token themselves.
    mochi_default_github_token: str | None = Field(default=None, exclude=True)

    # CORS -------------------------------------------------------------------
    cors_origins: str = "http://localhost:3000"

    @property
    def cors_origin_list(self) -> list[str]:
        return [o.strip() for o in self.cors_origins.split(",") if o.strip()]

    @property
    def is_prod(self) -> bool:
        return self.env.lower() == "prod"

    @property
    def sqlalchemy_url(self) -> str:
        return self.database_url


@lru_cache
def get_settings() -> Settings:
    return Settings()


settings = get_settings()