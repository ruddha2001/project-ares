import os
import re
import json
import logging
import subprocess
from datetime import datetime, timezone
from typing import Any, List, Optional

def normalize_vector(vector: List[float], target_dim: int = 1024) -> List[float]:
    if len(vector) < target_dim:
        return vector + [0.0] * (target_dim - len(vector))
    elif len(vector) > target_dim:
        return vector[:target_dim]
    return vector

# ---------------------------------------------------------------------------
# Copilot API session token — module-level in-memory cache
# ---------------------------------------------------------------------------
_copilot_token_cache: dict = {}  # keys: 'token', 'expires_at' (datetime)


def _exchange_copilot_token(github_token: str) -> str:
    """
    Exchanges a GitHub PAT for a short-lived Copilot session token.
    Caches the result in-memory with a 5-minute safety buffer before expiry.
    Raises RuntimeError with user-facing instructions if the exchange fails.
    """
    import httpx

    now = datetime.now(timezone.utc)
    cached_expires = _copilot_token_cache.get('expires_at')
    if cached_expires and cached_expires > now:
        return _copilot_token_cache['token']

    try:
        with httpx.Client(timeout=15.0) as client:
            response = client.get(
                'https://api.github.com/copilot_internal/v2/token',
                headers={
                    'Authorization': f'token {github_token}',
                    'Accept': 'application/json',
                    'Editor-Version': 'ARES/2.0',
                    'Editor-Plugin-Version': 'ares-worker/1.0',
                },
            )
    except Exception as e:
        raise RuntimeError(
            f"Copilot token exchange request failed: {e}\n"
            "Ensure network access to api.github.com, or unset COPILOT_MODEL to use Ollama instead."
        ) from e

    if response.status_code != 200:
        raise RuntimeError(
            f"Copilot token exchange failed (HTTP {response.status_code}).\n"
            f"Response: {response.text[:200]}\n"
            "Ensure your GITHUB_PAT is valid and has Copilot access, "
            "or unset COPILOT_MODEL to use Ollama instead."
        )

    data = response.json()
    token = data.get('token')
    if not token:
        raise RuntimeError(
            "Copilot token exchange returned no token.\n"
            "Ensure your GITHUB_PAT is valid and has Copilot access, "
            "or unset COPILOT_MODEL to use Ollama instead."
        )

    # Cache with 5-minute safety buffer
    BUFFER_SECONDS = 5 * 60
    expires_at_str = data.get('expires_at')
    if expires_at_str:
        from datetime import timedelta
        raw_expiry = datetime.fromisoformat(expires_at_str.replace('Z', '+00:00'))
        safe_expiry = raw_expiry - timedelta(seconds=BUFFER_SECONDS)
    else:
        from datetime import timedelta
        safe_expiry = now + timedelta(minutes=25)

    _copilot_token_cache['token'] = token
    _copilot_token_cache['expires_at'] = safe_expiry
    logging.info("Copilot session token acquired and cached.")
    return token


def fetch_embedding(
    text: str,
    github_token: Optional[str] = None,
    copilot_model: Optional[str] = None,
    model: Optional[str] = None
) -> List[float]:
    """
    If copilot_model is provided, calls the Copilot API embedding endpoint using a
    short-lived session token obtained by exchanging the GitHub PAT.
    Otherwise, defaults to querying the local Ollama inference service.
    Errors out loudly on any Copilot-path failure — does NOT fall back silently.
    """
    if copilot_model:
        import httpx

        token = github_token or os.environ.get("GITHUB_PAT") or os.environ.get("GITHUB_TOKEN")
        if not token:
            raise ValueError(
                f"COPILOT_MODEL is set to '{copilot_model}' but no GitHub token was found. "
                "Set GITHUB_PAT in your environment, or unset COPILOT_MODEL to use Ollama instead."
            )

        session_token = _exchange_copilot_token(token)

        try:
            with httpx.Client(timeout=30.0) as client:
                response = client.post(
                    'https://api.githubcopilot.com/embeddings',
                    headers={
                        'Authorization': f'Bearer {session_token}',
                        'Content-Type': 'application/json',
                        'Accept': 'application/json',
                        'Editor-Version': 'ARES/2.0',
                        'Editor-Plugin-Version': 'ares-worker/1.0',
                        'Copilot-Integration-Id': 'ares-worker',
                    },
                    json={
                        'model': copilot_model,
                        'input': text,
                        'encoding_format': 'float',
                    },
                )
        except Exception as e:
            raise RuntimeError(
                f"Copilot embeddings API request failed: {e}\n"
                "Check network access to api.githubcopilot.com, "
                "or unset COPILOT_MODEL to use Ollama instead."
            ) from e

        if response.status_code in (401, 403):
            # Invalidate cached token so next attempt re-exchanges
            _copilot_token_cache.clear()

        if response.status_code != 200:
            raise RuntimeError(
                f"Copilot embeddings API returned HTTP {response.status_code}.\n"
                f"Response: {response.text[:200]}\n"
                f"Check your GITHUB_PAT and COPILOT_MODEL ('{copilot_model}') configuration, "
                "or unset COPILOT_MODEL to use Ollama instead."
            )

        data = response.json()
        try:
            vector = data['data'][0]['embedding']
        except (KeyError, IndexError, TypeError) as e:
            raise RuntimeError(
                f"Copilot embeddings API returned an unexpected response format: {e}\n"
                f"Raw response: {str(data)[:300]}"
            ) from e

        return normalize_vector([float(x) for x in vector], 1024)

    else:
        # Default to local Ollama
        ollama_url = os.environ.get("INFERENCE_URL", "http://inference-sidecar:11434")
        final_model = model or os.environ.get("CODE_EMBEDDING_MODEL", "nomic-embed-text")
        
        import httpx
        with httpx.Client(timeout=30.0) as client:
            try:
                # Try newer /api/embed endpoint
                embed_endpoint = f"{ollama_url.rstrip('/')}/api/embed"
                embed_payload: dict[str, Any] = {"model": final_model, "input": text}
                if "qwen" in final_model.lower():
                    embed_payload["dimensions"] = 1024
                    
                response = client.post(embed_endpoint, json=embed_payload)
                if response.status_code == 200:
                    vector = response.json()["embeddings"][0]
                    return normalize_vector(vector, 1024)
            except Exception as e:
                logging.warning(f"Newer /api/embed endpoint failed: {str(e)}. Falling back to /api/embeddings...")
                
            try:
                # Fallback to older /api/embeddings
                embeddings_endpoint = f"{ollama_url.rstrip('/')}/api/embeddings"
                embeddings_payload = {"model": final_model, "prompt": text}
                response = client.post(embeddings_endpoint, json=embeddings_payload)
                response.raise_for_status()
                vector = response.json()["embedding"]
                return normalize_vector(vector, 1024)
            except Exception as e:
                logging.error(f"Failed to fetch embedding from local Ollama: {e}")
                raise RuntimeError("Local Ollama embedding inference failed.") from e

def fetch_completion(
    prompt: str,
    github_token: Optional[str] = None,
    copilot_model: Optional[str] = None
) -> str:
    """
    If copilot_model is provided, query it via the Copilot CLI.
    Otherwise, default to querying the local Ollama inference service.
    """
    if copilot_model:
        token = github_token or os.environ.get("GITHUB_PAT") or os.environ.get("GITHUB_TOKEN")
        if not token:
            raise ValueError("GitHub token is required to invoke Copilot CLI completion inside the container.")

        cmd = ["copilot", "-p", prompt, "-s", "--no-ask-user", "--model", copilot_model]
        
        env = os.environ.copy()
        env["HOME"] = "/tmp"
        env["COPILOT_GITHUB_TOKEN"] = token
        env.pop("GH_TOKEN", None)
        env.pop("GITHUB_TOKEN", None)

        try:
            result = subprocess.run(
                cmd,
                env=env,
                capture_output=True,
                text=True,
                check=True
            )
            return result.stdout.strip()
        except subprocess.CalledProcessError as e:
            logging.error(f"Copilot CLI completion execution failed: {e.stderr}")
            raise RuntimeError("Copilot CLI completion failed.") from e

    else:
        # Default to local Ollama
        ollama_url = os.environ.get("INFERENCE_URL", "http://inference-sidecar:11434")
        endpoint = f"{ollama_url.rstrip('/')}/api/generate"
        payload = {"model": "llama3", "prompt": prompt, "stream": False}
        
        try:
            import httpx
            with httpx.Client(timeout=60.0) as client:
                response = client.post(endpoint, json=payload)
                response.raise_for_status()
                return response.json()["response"].strip()
        except Exception as e:
            logging.error(f"Failed to fetch completion from local Ollama at {endpoint}: {e}")
            raise RuntimeError("Local Ollama completion failed.") from e

