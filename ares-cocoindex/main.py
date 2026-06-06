import os
import shutil
import asyncio  # ⚡ Shifted to async I/O loops
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

app = FastAPI(
    title="ARES Ingestion Worker Engine",
    description="Asynchronous multi-tenant codebase chunking and indexing fabric.",
    version="1.0.0",
)


class IngestionTask(BaseModel):
    projectId: str = Field(..., description="Target database tracking UUID namespace.")
    gitUrl: str = Field(
        ..., description="Absolute remote or loopback git repository URL."
    )
    branch: str = Field(
        default="main", description="Target tracking branch checkout limit."
    )


@app.post("/api/v1/etl/sync")
async def execute_incremental_sync(task: IngestionTask):
    """
    Asynchronous Ingestion Webhook.
    Spins up an isolated scratch directory, forks a non-blocking background
    subprocess for CocoIndex CDC sweeps, and guarantees sandboxed cleanup.
    """
    scratch_workspace = f"/tmp/ares_scratch/{task.projectId}"

    try:
        if os.path.exists(scratch_workspace):
            shown_rm = lambda: shutil.rmtree(scratch_workspace)
            await asyncio.to_thread(shown_rm)  # Keeps loop active during disk I/O

        print(
            f"📥 [ARES-WORKER] Launching parallel shallow clone for Project: {task.projectId}"
        )

        clone_process = await asyncio.create_subprocess_exec(
            "git",
            "clone",
            "--depth",
            "1",
            "--branch",
            task.branch,
            task.gitUrl,
            scratch_workspace,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
        )
        stdout_clone, stderr_clone = await clone_process.communicate()

        if clone_process.returncode != 0:
            raise Exception(f"Git clone failed: {stderr_clone.decode().strip()}")

        pipeline_env = os.environ.copy()
        pipeline_env["ARES_TARGET_WORKSPACE"] = scratch_workspace
        pipeline_env["ARES_CURRENT_PROJECT_ID"] = task.projectId

        print(
            f"🔄 [ARES-WORKER] Forking simultaneous CocoIndex CDC subprocess for Project: {task.projectId}..."
        )

        sync_process = await asyncio.create_subprocess_exec(
            "cocoindex",
            "update",
            "pipeline.py",
            env=pipeline_env,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
        )
        stdout_sync, stderr_sync = await sync_process.communicate()

        if sync_process.returncode != 0:
            raise Exception(
                f"CocoIndex execution failed: {stderr_sync.decode().strip()}"
            )

        print(
            f"✨ [ARES-WORKER] Parallel transaction completed for Project: {task.projectId}"
        )
        return {
            "status": "SUCCESS",
            "projectId": task.projectId,
            "summary": "Incremental codebase index synchronization finalized in parallel.",
        }

    except Exception as e:
        print(
            f"❌ [ARES-WORKER] Parallel pipeline failure for Project {task.projectId}: {str(e)}"
        )
        raise HTTPException(status_code=500, detail=str(e))

    finally:
        if os.path.exists(scratch_workspace):
            print(
                f"🧹 [ARES-WORKER] Scrubbing isolated assets for Project: {task.projectId}"
            )
            scrub = lambda: shutil.rmtree(scratch_workspace)
            await asyncio.to_thread(scrub)
