from fastapi import FastAPI, BackgroundTasks, HTTPException
from pydantic import BaseModel
import uuid
import logging

from codebase import execute_codebase_gauntlet
from document import execute_document_gauntlet

app = FastAPI(title="ARES Inference Worker Gateway")
logging.basicConfig(level=logging.INFO)


class CodebasePayload(BaseModel):
    job_id: str
    project_id: str
    repo_url: str
    default_branch: str


class DocumentPayload(BaseModel):
    job_id: str
    project_id: str
    source_origin: str
    source_url: str


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
    )

    return {"status": "PROCESSING", "job_id": payload.job_id}
