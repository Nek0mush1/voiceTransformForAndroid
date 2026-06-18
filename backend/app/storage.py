from __future__ import annotations

import json
import os
import sqlite3
from contextlib import contextmanager
from dataclasses import dataclass
from datetime import UTC, datetime
from pathlib import Path
from collections.abc import Iterator
from typing import Any


LLM_WIRE_API_CHAT_COMPLETIONS = "chat_completions"
LLM_WIRE_API_RESPONSES = "responses"
DEFAULT_LLM_WIRE_API = LLM_WIRE_API_CHAT_COMPLETIONS

DEFAULT_PROFILE = (
    "计算机专业大二学生，正在学习计组、计网、操作系统、数据结构、Agent 开发。"
)

DEFAULT_TERMS = [
    ("计组", "course", ["祭祖", "祭祖课", "计组课"], 1.0),
    ("计网", "course", ["鸡王", "计网课"], 1.0),
    ("操作系统", "course", ["炒作系统"], 1.0),
    ("数据结构", "course", ["数据解构"], 1.0),
    ("数据库", "course", ["书局库", "数据哭"], 1.0),
    ("Agent", "ai", ["真特", "智能体"], 1.0),
    ("RAG", "ai", ["拉格"], 1.0),
    ("Cache", "system", ["cash", "快取"], 1.0),
    ("Transformer", "ai", ["transformer"], 1.0),
]


def now_iso() -> str:
    return datetime.now(UTC).isoformat()


def database_path() -> Path:
    configured = os.getenv("VOICE_TRANSFORM_DB")
    if configured:
        return Path(configured)
    return Path(__file__).resolve().parents[1] / "data" / "voice_transform.db"


def connect() -> sqlite3.Connection:
    path = database_path()
    path.parent.mkdir(parents=True, exist_ok=True)
    connection = sqlite3.connect(path)
    connection.row_factory = sqlite3.Row
    return connection


@contextmanager
def db_connection() -> Iterator[sqlite3.Connection]:
    connection = connect()
    try:
        yield connection
        connection.commit()
    except Exception:
        connection.rollback()
        raise
    finally:
        connection.close()


def init_db() -> None:
    with db_connection() as connection:
        connection.executescript(
            """
            CREATE TABLE IF NOT EXISTS profiles (
                user_id TEXT PRIMARY KEY,
                profile_text TEXT NOT NULL,
                updated_at TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS terms (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id TEXT NOT NULL,
                term TEXT NOT NULL,
                category TEXT NOT NULL DEFAULT '',
                aliases_json TEXT NOT NULL DEFAULT '[]',
                weight REAL NOT NULL DEFAULT 1.0,
                created_at TEXT NOT NULL,
                UNIQUE(user_id, term)
            );

            CREATE TABLE IF NOT EXISTS traces (
                id TEXT PRIMARY KEY,
                user_id TEXT NOT NULL,
                raw_text TEXT NOT NULL,
                corrected_text TEXT NOT NULL,
                profile_summary TEXT NOT NULL,
                matched_terms_json TEXT NOT NULL,
                pinyin_candidates_json TEXT NOT NULL,
                llm_success INTEGER NOT NULL,
                llm_error TEXT NOT NULL,
                tools_json TEXT NOT NULL,
                created_at TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS llm_config (
                id INTEGER PRIMARY KEY CHECK (id = 1),
                base_url TEXT NOT NULL DEFAULT '',
                api_key TEXT NOT NULL DEFAULT '',
                model TEXT NOT NULL DEFAULT '',
                wire_api TEXT NOT NULL DEFAULT 'chat_completions',
                updated_at TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS llm_call_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                trace_id TEXT NOT NULL,
                user_id TEXT NOT NULL,
                raw_text TEXT NOT NULL,
                fallback_text TEXT NOT NULL,
                output_text TEXT NOT NULL,
                success INTEGER NOT NULL,
                error TEXT NOT NULL,
                correction_method TEXT NOT NULL,
                base_url TEXT NOT NULL,
                model TEXT NOT NULL,
                wire_api TEXT NOT NULL,
                duration_ms INTEGER NOT NULL,
                created_at TEXT NOT NULL
            );
            """
        )
        ensure_llm_config_schema(connection)
        seed_defaults(connection)


def ensure_llm_config_schema(connection: sqlite3.Connection) -> None:
    columns = {
        row["name"]
        for row in connection.execute("PRAGMA table_info(llm_config)").fetchall()
    }
    if "wire_api" not in columns:
        connection.execute(
            """
            ALTER TABLE llm_config
            ADD COLUMN wire_api TEXT NOT NULL DEFAULT 'chat_completions'
            """
        )


def seed_defaults(connection: sqlite3.Connection) -> None:
    timestamp = now_iso()
    connection.execute(
        """
        INSERT OR IGNORE INTO profiles (user_id, profile_text, updated_at)
        VALUES (?, ?, ?)
        """,
        ("local_user", DEFAULT_PROFILE, timestamp),
    )
    for term, category, aliases, weight in DEFAULT_TERMS:
        connection.execute(
            """
            INSERT OR IGNORE INTO terms
            (user_id, term, category, aliases_json, weight, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            ("local_user", term, category, json.dumps(aliases, ensure_ascii=False), weight, timestamp),
        )


@dataclass(frozen=True)
class ProfileRecord:
    user_id: str
    profile_text: str
    updated_at: str


@dataclass(frozen=True)
class TermRecord:
    id: int
    user_id: str
    term: str
    category: str
    aliases: list[str]
    weight: float
    created_at: str


@dataclass(frozen=True)
class TraceRecord:
    id: str
    user_id: str
    raw_text: str
    corrected_text: str
    profile_summary: str
    matched_terms: list[str]
    pinyin_candidates: list[dict[str, Any]]
    llm_success: bool
    llm_error: str
    tools: list[dict[str, Any]]
    created_at: str


@dataclass(frozen=True)
class LLMCallLogRecord:
    id: int
    trace_id: str
    user_id: str
    raw_text: str
    fallback_text: str
    output_text: str
    success: bool
    error: str
    correction_method: str
    base_url: str
    model: str
    wire_api: str
    duration_ms: int
    created_at: str


@dataclass(frozen=True)
class LLMConfigRecord:
    base_url: str
    api_key: str
    model: str
    wire_api: str
    updated_at: str | None


def row_to_profile(row: sqlite3.Row) -> ProfileRecord:
    return ProfileRecord(
        user_id=row["user_id"],
        profile_text=row["profile_text"],
        updated_at=row["updated_at"],
    )


def row_to_term(row: sqlite3.Row) -> TermRecord:
    return TermRecord(
        id=row["id"],
        user_id=row["user_id"],
        term=row["term"],
        category=row["category"],
        aliases=json.loads(row["aliases_json"]),
        weight=row["weight"],
        created_at=row["created_at"],
    )


def get_profile(user_id: str) -> ProfileRecord:
    init_db()
    with db_connection() as connection:
        row = connection.execute(
            "SELECT user_id, profile_text, updated_at FROM profiles WHERE user_id = ?",
            (user_id,),
        ).fetchone()
        if row is None:
            timestamp = now_iso()
            connection.execute(
                """
                INSERT INTO profiles (user_id, profile_text, updated_at)
                VALUES (?, ?, ?)
                """,
                (user_id, DEFAULT_PROFILE, timestamp),
            )
            return ProfileRecord(user_id=user_id, profile_text=DEFAULT_PROFILE, updated_at=timestamp)
        return row_to_profile(row)


def update_profile(user_id: str, profile_text: str) -> ProfileRecord:
    init_db()
    timestamp = now_iso()
    with db_connection() as connection:
        connection.execute(
            """
            INSERT INTO profiles (user_id, profile_text, updated_at)
            VALUES (?, ?, ?)
            ON CONFLICT(user_id) DO UPDATE SET
                profile_text = excluded.profile_text,
                updated_at = excluded.updated_at
            """,
            (user_id, profile_text, timestamp),
        )
    return ProfileRecord(user_id=user_id, profile_text=profile_text, updated_at=timestamp)


def list_terms(user_id: str | None = None) -> list[TermRecord]:
    init_db()
    with db_connection() as connection:
        if user_id:
            rows = connection.execute(
                """
                SELECT id, user_id, term, category, aliases_json, weight, created_at
                FROM terms
                WHERE user_id = ?
                ORDER BY id
                """,
                (user_id,),
            ).fetchall()
        else:
            rows = connection.execute(
                """
                SELECT id, user_id, term, category, aliases_json, weight, created_at
                FROM terms
                ORDER BY user_id, id
                """
            ).fetchall()
        return [row_to_term(row) for row in rows]


def create_term(
    user_id: str,
    term: str,
    category: str = "",
    aliases: list[str] | None = None,
    weight: float = 1.0,
) -> TermRecord:
    init_db()
    timestamp = now_iso()
    aliases = aliases or []
    aliases_json = json.dumps(aliases, ensure_ascii=False)
    with db_connection() as connection:
        connection.execute(
            """
            INSERT INTO terms (user_id, term, category, aliases_json, weight, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(user_id, term) DO UPDATE SET
                category = excluded.category,
                aliases_json = excluded.aliases_json,
                weight = excluded.weight
            """,
            (user_id, term, category, aliases_json, weight, timestamp),
        )
        row = connection.execute(
            """
            SELECT id, user_id, term, category, aliases_json, weight, created_at
            FROM terms
            WHERE user_id = ? AND term = ?
            """,
            (user_id, term),
        ).fetchone()
        return row_to_term(row)


def delete_term(term_id: int) -> bool:
    init_db()
    with db_connection() as connection:
        cursor = connection.execute("DELETE FROM terms WHERE id = ?", (term_id,))
        return cursor.rowcount > 0


def save_trace(
    trace_id: str,
    user_id: str,
    raw_text: str,
    corrected_text: str,
    profile_summary: str,
    matched_terms: list[str],
    pinyin_candidates: list[dict[str, Any]],
    llm_success: bool,
    llm_error: str,
    tools: list[dict[str, Any]],
) -> None:
    init_db()
    with db_connection() as connection:
        connection.execute(
            """
            INSERT INTO traces
            (
                id,
                user_id,
                raw_text,
                corrected_text,
                profile_summary,
                matched_terms_json,
                pinyin_candidates_json,
                llm_success,
                llm_error,
                tools_json,
                created_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                trace_id,
                user_id,
                raw_text,
                corrected_text,
                profile_summary,
                json.dumps(matched_terms, ensure_ascii=False),
                json.dumps(pinyin_candidates, ensure_ascii=False),
                1 if llm_success else 0,
                llm_error,
                json.dumps(tools, ensure_ascii=False),
                now_iso(),
            ),
        )


def list_traces(limit: int = 20) -> list[TraceRecord]:
    init_db()
    with db_connection() as connection:
        rows = connection.execute(
            """
            SELECT id, user_id, raw_text, corrected_text, profile_summary,
                   matched_terms_json, pinyin_candidates_json, llm_success,
                   llm_error, tools_json, created_at
            FROM traces
            ORDER BY created_at DESC
            LIMIT ?
            """,
            (limit,),
        ).fetchall()
        return [
            TraceRecord(
                id=row["id"],
                user_id=row["user_id"],
                raw_text=row["raw_text"],
                corrected_text=row["corrected_text"],
                profile_summary=row["profile_summary"],
                matched_terms=json.loads(row["matched_terms_json"]),
                pinyin_candidates=json.loads(row["pinyin_candidates_json"]),
                llm_success=bool(row["llm_success"]),
                llm_error=row["llm_error"],
                tools=json.loads(row["tools_json"]),
                created_at=row["created_at"],
            )
            for row in rows
        ]


def save_llm_call_log(
    trace_id: str,
    user_id: str,
    raw_text: str,
    fallback_text: str,
    output_text: str,
    success: bool,
    error: str,
    correction_method: str,
    base_url: str,
    model: str,
    wire_api: str,
    duration_ms: int,
) -> None:
    init_db()
    with db_connection() as connection:
        connection.execute(
            """
            INSERT INTO llm_call_logs
            (
                trace_id,
                user_id,
                raw_text,
                fallback_text,
                output_text,
                success,
                error,
                correction_method,
                base_url,
                model,
                wire_api,
                duration_ms,
                created_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                trace_id,
                user_id,
                raw_text,
                fallback_text,
                output_text,
                1 if success else 0,
                error,
                correction_method,
                base_url,
                model,
                wire_api,
                duration_ms,
                now_iso(),
            ),
        )
        connection.execute(
            """
            DELETE FROM llm_call_logs
            WHERE id NOT IN (
                SELECT id FROM llm_call_logs
                ORDER BY created_at DESC, id DESC
                LIMIT 50
            )
            """
        )


def list_llm_call_logs(limit: int = 50) -> list[LLMCallLogRecord]:
    init_db()
    bounded_limit = max(1, min(limit, 50))
    with db_connection() as connection:
        rows = connection.execute(
            """
            SELECT id, trace_id, user_id, raw_text, fallback_text, output_text,
                   success, error, correction_method, base_url, model, wire_api,
                   duration_ms, created_at
            FROM llm_call_logs
            ORDER BY created_at DESC, id DESC
            LIMIT ?
            """,
            (bounded_limit,),
        ).fetchall()
        return [
            LLMCallLogRecord(
                id=row["id"],
                trace_id=row["trace_id"],
                user_id=row["user_id"],
                raw_text=row["raw_text"],
                fallback_text=row["fallback_text"],
                output_text=row["output_text"],
                success=bool(row["success"]),
                error=row["error"],
                correction_method=row["correction_method"],
                base_url=row["base_url"],
                model=row["model"],
                wire_api=row["wire_api"],
                duration_ms=row["duration_ms"],
                created_at=row["created_at"],
            )
            for row in rows
        ]


def get_llm_config() -> LLMConfigRecord:
    init_db()
    with db_connection() as connection:
        row = connection.execute(
            """
            SELECT base_url, api_key, model, wire_api, updated_at
            FROM llm_config
            WHERE id = 1
            """
        ).fetchone()
    if row is not None:
        return LLMConfigRecord(
            base_url=row["base_url"],
            api_key=row["api_key"],
            model=row["model"],
            wire_api=normalize_llm_wire_api(row["wire_api"]),
            updated_at=row["updated_at"],
        )
    return LLMConfigRecord(
        base_url=os.getenv("LLM_BASE_URL", "").strip().rstrip("/"),
        api_key=os.getenv("LLM_API_KEY", "").strip(),
        model=os.getenv("LLM_MODEL", "").strip(),
        wire_api=normalize_llm_wire_api(os.getenv("LLM_WIRE_API", DEFAULT_LLM_WIRE_API)),
        updated_at=None,
    )


def update_llm_config(
    base_url: str,
    api_key: str | None,
    model: str,
    wire_api: str = DEFAULT_LLM_WIRE_API,
) -> LLMConfigRecord:
    init_db()
    current = get_llm_config()
    timestamp = now_iso()
    normalized_base_url = (base_url or "").strip().rstrip("/")
    normalized_model = (model or "").strip()
    normalized_api_key = current.api_key if api_key is None else api_key.strip()
    normalized_wire_api = normalize_llm_wire_api(wire_api)
    with db_connection() as connection:
        connection.execute(
            """
            INSERT INTO llm_config (id, base_url, api_key, model, wire_api, updated_at)
            VALUES (1, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                base_url = excluded.base_url,
                api_key = excluded.api_key,
                model = excluded.model,
                wire_api = excluded.wire_api,
                updated_at = excluded.updated_at
            """,
            (normalized_base_url, normalized_api_key, normalized_model, normalized_wire_api, timestamp),
        )
    return LLMConfigRecord(
        base_url=normalized_base_url,
        api_key=normalized_api_key,
        model=normalized_model,
        wire_api=normalized_wire_api,
        updated_at=timestamp,
    )


def normalize_llm_wire_api(wire_api: str | None) -> str:
    value = (wire_api or "").strip().lower().replace("-", "_")
    if value in {"responses", "response"}:
        return LLM_WIRE_API_RESPONSES
    if value in {"chat", "chat_completion", "chat_completions"}:
        return LLM_WIRE_API_CHAT_COMPLETIONS
    return DEFAULT_LLM_WIRE_API
