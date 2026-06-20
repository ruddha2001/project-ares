import os
import re
import logging
import httpx
import psycopg2
import json
from typing import Optional, List


def chunk_document_text(
    text: str, chunk_size: int = 500, chunk_overlap: int = 50
) -> List[str]:
    """
    Splits raw document text into clean, overlapping blocks.
    Prioritizes splitting on structural markdown headers or double newlines
    to maintain semantic cohesion, falling back to word windows if necessary.
    """
    text = re.sub(r"\n{3,}", "\n\n", text)

    paragraphs = text.split("\n\n")
    chunks = []
    current_chunk = []
    current_word_count = 0

    for para in paragraphs:
        para_words = para.split()
        if not para_words:
            continue

        if len(para_words) > chunk_size:
            if current_chunk:
                chunks.append(" ".join(current_chunk))
                current_chunk = []
                current_word_count = 0

            for i in range(0, len(para_words), chunk_size - chunk_overlap):
                word_slice = para_words[i : i + chunk_size]
                chunks.append(" ".join(word_slice))
            continue

        if current_word_count + len(para_words) > chunk_size:
            chunks.append(" ".join(current_chunk))
            overlap_words = (
                current_chunk[-chunk_overlap:]
                if len(current_chunk) >= chunk_overlap
                else current_chunk
            )
            current_chunk = list(overlap_words) + para_words
            current_word_count = len(current_chunk)
        else:
            current_chunk.extend(para_words)
            current_word_count += len(para_words)

    if current_chunk:
        chunks.append(" ".join(current_chunk))

    return [c.strip() for c in chunks if c.strip()]





from connectors import resolve_document_connector_by_origin
from embeddings import fetch_embedding


def execute_document_gauntlet(
    job_id: str,
    project_id: str,
    source_origin: str,
    source_url: str,
    document_token: Optional[str] = None,
    github_token: Optional[str] = None,
    copilot_embedding_model: Optional[str] = None,
    copilot_llm_model: Optional[str] = None,
):
    logging.info(
        f"Starting authentic Document Ingestion for Job: {job_id} -> Target: {source_url}"
    )

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

    try:
        logging.info("Librarian executing secure text harvest via HTTPX...")
        with httpx.Client(follow_redirects=True, timeout=20.0) as client:
            connector = resolve_document_connector_by_origin(source_origin, source_url)
            document = connector.fetch(client, source_url, document_token)
            raw_content = document.content

        logging.info("Partitioning document into semantic text chunks...")
        text_chunks = chunk_document_text(raw_content, chunk_size=500, chunk_overlap=50)
        logging.info(
            f"Generated {len(text_chunks)} distinct document snippets for indexing."
        )

        if not text_chunks:
            logging.warning(
                "No text extracted from target document endpoint. Aborting database sync."
            )
            return

        logging.info("Opening direct psycopg2 connection to database...")
        conn = psycopg2.connect(db_url)
        try:
            with conn.cursor() as cur:
                doc_model = os.environ.get("DOC_EMBEDDING_MODEL", "bge-m3")
                for idx, chunk in enumerate(text_chunks):
                    logging.info(
                        f"Extracting embedding for chunk {idx + 1}/{len(text_chunks)}..."
                    )
                    embedding_vector = fetch_embedding(
                        chunk,
                        github_token=github_token,
                        copilot_model=copilot_embedding_model,
                        model=doc_model
                    )
                    import uuid
                    from datetime import datetime

                    insert_query = """
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
                        VALUES (%s, %s, %s, %s, %s, %s, %s::vector(1024), %s, %s);
                    """

                    cur.execute(
                        insert_query,
                        (
                            str(uuid.uuid4()),
                            project_id,
                            source_origin,
                            source_url,
                            f"{document.title}#chunk_{idx + 1}",
                            chunk,
                            json.dumps(embedding_vector, separators=(",", ":")),
                            datetime.now(),
                            datetime.now(),
                        ),
                    )

            conn.commit()
            logging.info(
                f"Document ingestion completed successfully. Synchronized {len(text_chunks)} indices."
            )

        finally:
            conn.close()

    except Exception as e:
        logging.error(f"Fatal crash inside Document Ingestion lifecycle: {str(e)}")
        raise e
