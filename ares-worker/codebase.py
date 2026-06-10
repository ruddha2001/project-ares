import json
import logging
import os
import shutil
import subprocess
from pathlib import Path

import httpx
import psycopg2
from typing import Optional
from embeddings import fetch_embedding as fetch_api_embedding


def execute_codebase_gauntlet(
    job_id: str,
    project_id: str,
    repo_url: str,
    default_branch: str,
    github_token: Optional[str] = None,
    copilot_model: Optional[str] = None,
):
    workspace_dir = f"/tmp/ares_workspace_{job_id}/codebase"
    logging.info(f"Starting Codebase Gauntlet for Job {job_id} inside {workspace_dir}")

    def iter_repository_files(root: Path):
        ignored_dirs = {
            ".git",
            ".gradle",
            "build",
            "dist",
            "node_modules",
            "target",
            "venv",
            ".venv",
            "__pycache__",
        }
        for path in root.rglob("*"):
            if not path.is_file():
                continue
            if any(part in ignored_dirs for part in path.parts):
                continue
            yield path

    def chunk_text(text: str, chunk_size: int = 500, chunk_overlap: int = 50):
        if chunk_size <= 0:
            return

        start = 0
        text_length = len(text)
        while start < text_length:
            end = min(text_length, start + chunk_size)
            chunk = text[start:end].strip()
            if chunk:
                yield chunk
            if end >= text_length:
                break
            start = max(end - chunk_overlap, start + 1)



    try:
        if os.path.exists(workspace_dir):
            shutil.rmtree(workspace_dir)
        os.makedirs(workspace_dir, exist_ok=True)

        logging.info(f"Workspace path successfully allocated: {workspace_dir}")

        if "github.com" in repo_url and not repo_url.startswith("git@"):
            clean_url = repo_url.replace("https://", "").replace("http://", "")
            if github_token:
                authenticated_url = f"https://{github_token}@{clean_url}"
            else:
                authenticated_url = f"https://{clean_url}"
        else:
            authenticated_url = repo_url

        logging.info("Librarian spawning authenticated shallow git clone...")
        subprocess.run(
            [
                "git",
                "clone",
                "--depth",
                "1",
                "--branch",
                default_branch,
                authenticated_url,
                workspace_dir,
            ],
            check=True,
            capture_output=True,
        )
        logging.info("Repository successfully cloned into isolated workspace.")

        try:
            db_host = os.environ.get("DB_HOST", "postgres-cluster")
            db_port = os.environ.get("DB_PORT", "5432")
            db_name = os.environ.get("DB_NAME", "ares")
            db_user = os.environ["DB_USER"]
            db_password = os.environ["DB_PASSWORD"]
        except KeyError as missing_var:
            raise RuntimeError(
                f"Missing required database environment variable: {missing_var.args[0]}"
            ) from missing_var

        db_url = f"postgresql://{db_user}:{db_password}@{db_host}:{db_port}/{db_name}"

        logging.info(
            "Invoking Copilot CLI for embedding extraction..."
        )

        rows_written = 0
        with psycopg2.connect(db_url) as connection:
            with connection.cursor() as cursor:
                with httpx.Client(timeout=120.0) as client:
                    for file_path in iter_repository_files(Path(workspace_dir)):
                        try:
                            file_text = file_path.read_text(encoding="utf-8")
                        except UnicodeDecodeError:
                            continue

                        relative_path = file_path.relative_to(workspace_dir).as_posix()
                        for chunk_index, chunk in enumerate(
                            chunk_text(file_text), start=1
                        ):
                            embedding = fetch_api_embedding(
                                chunk,
                                github_token=github_token,
                                copilot_model=copilot_model
                            )
                            import uuid
                            from datetime import datetime

                            embedding_literal = json.dumps(
                                embedding, separators=(",", ":")
                            )
                            cursor.execute(
                                """
                                INSERT INTO ares_knowledge_indices (
                                    id,
                                    project_id,
                                    source_origin,
                                    source_url,
                                    block_title,
                                    block_content,
                                    embedding,
                                    created_at,
                                    updated_at
                                )
                                VALUES (%s, %s, %s, %s, %s, %s, %s::vector(768), %s, %s)
                                """,
                                (
                                    str(uuid.uuid4()),
                                    project_id,
                                    "LOCAL_CODEBASE",
                                    repo_url,
                                    f"{relative_path}#{chunk_index}",
                                    chunk,
                                    embedding_literal,
                                    datetime.now(),
                                    datetime.now(),
                                ),
                            )
                            rows_written += 1

        logging.info(
            f"Codebase ingestion completed successfully for Job: {job_id}. "
            f"Stored {rows_written} chunks."
        )

    except subprocess.CalledProcessError as e:
        error_log = e.stderr.decode().replace(github_token, "********")
        logging.error(f"Git runtime crash during execution loop: {error_log}")
        raise RuntimeError("Authenticated Git operation failed.")

    except Exception as e:
        logging.error(
            f"Fatal error encountered down the ingestion lifecycle chain: {str(e)}"
        )
        raise e
    finally:
        if os.path.exists(workspace_dir):
            shutil.rmtree(workspace_dir)
            logging.info(f"Cleaned up workspace for Job {job_id}")
