from fastapi import FastAPI, BackgroundTasks, HTTPException
from pydantic import BaseModel
import logging
from typing import Optional

from codebase import execute_codebase_gauntlet
from document import execute_document_gauntlet

app = FastAPI(title="ARES Inference Worker Gateway")
logging.basicConfig(level=logging.INFO)


class CodebasePayload(BaseModel):
    job_id: str
    project_id: str
    repo_url: str
    default_branch: str
    github_token: Optional[str] = None
    copilot_model: Optional[str] = None


class DocumentPayload(BaseModel):
    job_id: str
    project_id: str
    source_origin: str
    source_url: str
    document_token: Optional[str] = None
    github_token: Optional[str] = None
    copilot_model: Optional[str] = None

class EmbeddingPayload(BaseModel):
    prompt: str
    github_token: Optional[str] = None
    copilot_model: Optional[str] = None
    is_code: Optional[bool] = True


@app.post("/api/embeddings")
def get_embeddings(payload: EmbeddingPayload):
    try:
        import os
        from embeddings import fetch_embedding
        model = os.environ.get("CODE_EMBEDDING_MODEL", "nomic-embed-text") if payload.is_code else os.environ.get("DOC_EMBEDDING_MODEL", "bge-m3")
        vector = fetch_embedding(
            payload.prompt,
            github_token=payload.github_token,
            copilot_model=payload.copilot_model,
            model=model
        )
        return {"embedding": vector}
    except Exception as e:
        logging.error(f"Error generating embeddings: {str(e)}")
        raise HTTPException(status_code=500, detail=str(e))

class GeneratePayload(BaseModel):
    prompt: str
    github_token: Optional[str] = None
    copilot_model: Optional[str] = None

@app.post("/api/generate")
def generate_text(payload: GeneratePayload):
    try:
        from embeddings import fetch_completion
        response_text = fetch_completion(
            payload.prompt,
            github_token=payload.github_token,
            copilot_model=payload.copilot_model
        )
        return {"response": response_text}
    except Exception as e:
        logging.error(f"Error generating text: {str(e)}")
        raise HTTPException(status_code=500, detail=str(e))



@app.post("/api/v1/etl/codebase", status_code=202)
def trigger_codebase_ingestion(
    payload: CodebasePayload, background_tasks: BackgroundTasks
):
    logging.info(
        f"Accepted codebase job {payload.job_id} for project {payload.project_id}"
    )

    # Forward the identical orchestrator tracking key directly down to the gauntlet worker
    background_tasks.add_task(
        execute_codebase_gauntlet,
        payload.job_id,
        payload.project_id,
        payload.repo_url,
        payload.default_branch,
        payload.github_token,
        payload.copilot_model,
    )

    return {"status": "PROCESSING", "job_id": payload.job_id}


@app.post("/api/v1/etl/document", status_code=202)
def trigger_document_ingestion(
    payload: DocumentPayload, background_tasks: BackgroundTasks
):
    logging.info(
        f"Accepted document job {payload.job_id} for project {payload.project_id}"
    )

    # Forward the identical orchestrator tracking key directly down to the gauntlet worker
    background_tasks.add_task(
        execute_document_gauntlet,
        payload.job_id,
        payload.project_id,
        payload.source_origin,
        payload.source_url,
        payload.document_token,
        payload.github_token,
        payload.copilot_model,
    )

    return {"status": "PROCESSING", "job_id": payload.job_id}
