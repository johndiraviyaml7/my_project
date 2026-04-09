"""
QuantixMed — Database layer (psycopg2 + connection pooling)
"""
import logging
import psycopg2
import psycopg2.extras
from psycopg2 import pool
from pathlib import Path
from contextlib import contextmanager

from config.settings import DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD

logger = logging.getLogger(__name__)

_pool: pool.SimpleConnectionPool | None = None


def init_pool(minconn: int = 2, maxconn: int = 10) -> None:
    global _pool
    _pool = pool.SimpleConnectionPool(
        minconn, maxconn,
        host=DB_HOST, port=DB_PORT, dbname=DB_NAME,
        user=DB_USER, password=DB_PASSWORD,
        cursor_factory=psycopg2.extras.RealDictCursor,
    )
    logger.info("DB connection pool initialised (%d–%d)", minconn, maxconn)


@contextmanager
def get_conn():
    if _pool is None:
        init_pool()
    conn = _pool.getconn()
    try:
        yield conn
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        _pool.putconn(conn)


def init_schema() -> None:
    """Run the DDL schema script to create all tables."""
    sql_path = Path(__file__).resolve().parent.parent / "models" / "schema.sql"
    ddl = sql_path.read_text()
    with get_conn() as conn:
        with conn.cursor() as cur:
            cur.execute(ddl)
    logger.info("Database schema initialised")


def close_pool() -> None:
    if _pool:
        _pool.closeall()
        logger.info("DB pool closed")
