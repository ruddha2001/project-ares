import os
import cocoindex
from cocoindex import flow


@flow.flow_def(name="AresDistributedCodebaseIngestion")
def ares_ingestion_flow(
    flow_builder: cocoindex.FlowBuilder, data_scope: cocoindex.DataScope
):
    """
    State-driven incremental ingestion graph. Inspects temporary scratch
    workspaces, evaluates text footprints against central Postgres metadata,
    and streams dense vector indices directly into the app schema.
    """
    target_path = os.getenv("ARES_TARGET_WORKSPACE", "/tmp/invalid_fallback")
    current_project_id = os.getenv("ARES_CURRENT_PROJECT_ID")
    inference_service_url = os.getenv("INFERENCE_URL", "http://inference-sidecar:11434")

    data_scope["documents"] = flow_builder.add_source(
        cocoindex.sources.LocalFile(
            path=target_path,
            ignored_patterns=[
                "**/node_modules/**",
                "**/.git/**",
                "**/build/**",
                "**/target/**",
                "**/.gradle/**",
                "**/*.jar",
                "**/*.class",
                "**/*.lock",
                "**/dist/**",
                "**/*.png",
                "**/*.jpg",
                "**/*.jpeg",
                "**/*.ico",
                "**/*.zip",
                "**/*.tar.gz",
                "**/bun.lock",
                "**/gradlew",
                "**/gradlew.bat",
            ],
        )
    )

    code_embeddings = data_scope.add_collector()

    with data_scope["documents"].row() as doc:
        doc["chunks"] = doc["content"].transform(
            cocoindex.functions.SplitRecursively(), chunk_size=1200, chunk_overlap=150
        )

        with doc["chunks"].row() as chunk:
            chunk["vector"] = chunk["text"].transform(
                cocoindex.functions.OllamaEmbed(
                    url=inference_service_url, model="nomic-embed-text"
                )
            )

            code_embeddings.collect(
                project_id=current_project_id,
                source_origin="LOCAL_CODEBASE",
                source_url=doc["filename"],
                block_title=doc["filename"],
                block_content=chunk["text"],
                embedding=chunk["vector"],
            )

    code_embeddings.export(
        table_name="ares_knowledge_indices",
        storage=cocoindex.storages.Postgres(),
        primary_key_fields=["source_url"],
        vector_indexes=[
            cocoindex.VectorIndexDef(
                field_name="embedding",
                metric=cocoindex.VectorSimilarityMetric.COSINE_SIMILARITY,
            )
        ],
    )
