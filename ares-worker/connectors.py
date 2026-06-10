import base64
import logging
import re
from html import unescape
from urllib.parse import urlparse
import httpx
from dataclasses import dataclass
from typing import Optional, List, Protocol

NOTION_API_VERSION = "2026-03-11"


@dataclass(frozen=True)
class DocumentSource:
    title: str
    content: str


class DocumentConnector(Protocol):
    def matches(self, source_origin: str, source_url: str) -> bool: ...

    def fetch(
        self,
        client: httpx.Client,
        source_url: str,
        document_token: Optional[str],
    ) -> DocumentSource: ...


class HttpDocumentConnector:
    def matches(self, source_origin: str, source_url: str) -> bool:
        return True

    def fetch(
        self,
        client: httpx.Client,
        source_url: str,
        document_token: Optional[str],
    ) -> DocumentSource:
        headers = {"User-Agent": "Ares-Worker-Librarian/1.0"}
        if document_token and document_token.strip():
            headers["Authorization"] = f"Bearer {document_token.strip()}"

        response = client.get(source_url, headers=headers)
        response.raise_for_status()
        return DocumentSource(title=source_url, content=response.text)


class NotionConnector(HttpDocumentConnector):
    def matches(self, source_origin: str, source_url: str) -> bool:
        normalized_origin = source_origin.upper()
        return (
            normalized_origin == "NOTION"
            or "notion.so" in source_url
            or "notion.site" in source_url
        )

    def _build_headers(self, document_token: Optional[str]) -> dict[str, str]:
        if not document_token or not document_token.strip():
            raise RuntimeError("Notion connector requires a Notion API token.")
        return {
            "Authorization": f"Bearer {document_token.strip()}",
            "Notion-Version": NOTION_API_VERSION,
            "User-Agent": "Ares-Worker-Librarian/1.0",
        }

    def _extract_page_id(self, source_url: str) -> str:
        match = re.search(r"([0-9a-fA-F]{32})", source_url.replace("-", ""))
        if not match:
            raise RuntimeError(
                f"Unable to extract a Notion page id from URL: {source_url}"
            )
        return match.group(1)

    def _rich_text_to_plain(self, items: list[dict]) -> str:
        parts: list[str] = []
        for item in items:
            text = item.get("plain_text")
            if text:
                parts.append(text)
        return unescape("".join(parts)).strip()

    def _render_block(
        self, block: dict, client: httpx.Client, headers: dict[str, str]
    ) -> list[str]:
        block_type = block.get("type")
        block_data = block.get(block_type, {}) if block_type else {}
        lines: list[str] = []

        if block_type == "paragraph":
            text = self._rich_text_to_plain(block_data.get("rich_text", []))
            if text:
                lines.append(text)
        elif block_type in {"heading_1", "heading_2", "heading_3"}:
            text = self._rich_text_to_plain(block_data.get("rich_text", []))
            if text:
                prefix = {"heading_1": "#", "heading_2": "##", "heading_3": "###"}[
                    block_type
                ]
                lines.append(f"{prefix} {text}")
        elif block_type in {
            "bulleted_list_item",
            "numbered_list_item",
            "quote",
            "callout",
            "to_do",
            "toggle",
            "code",
        }:
            text = self._rich_text_to_plain(block_data.get("rich_text", []))
            if text:
                lines.append(text)
        elif block_type == "child_page":
            title = block_data.get("title")
            if title:
                lines.append(str(title))
        else:
            text = self._rich_text_to_plain(block_data.get("rich_text", []))
            if text:
                lines.append(text)

        if block.get("has_children"):
            child_blocks = self._fetch_block_children(
                client, headers, block.get("id", "")
            )
            for child in child_blocks:
                lines.extend(self._render_block(child, client, headers))

        return lines

    def _fetch_block_children(
        self, client: httpx.Client, headers: dict[str, str], block_id: str
    ) -> list[dict]:
        results: list[dict] = []
        start_cursor: Optional[str] = None

        while True:
            params: dict[str, str | int] = {"page_size": 100}
            if start_cursor:
                params["start_cursor"] = start_cursor

            response = client.get(
                f"https://api.notion.com/v1/blocks/{block_id}/children",
                headers=headers,
                params=params,
            )
            response.raise_for_status()
            payload = response.json()
            results.extend(payload.get("results", []))
            if not payload.get("has_more"):
                break
            start_cursor = payload.get("next_cursor")

        return results

    def _page_title(self, page: dict) -> str:
        properties = page.get("properties", {})
        for prop in properties.values():
            if prop.get("type") == "title":
                title_items = prop.get("title", [])
                title = self._rich_text_to_plain(title_items)
                if title:
                    return title
        return page.get("id", "Notion document")

    def fetch(
        self,
        client: httpx.Client,
        source_url: str,
        document_token: Optional[str],
    ) -> DocumentSource:
        headers = self._build_headers(document_token)
        page_id = self._extract_page_id(source_url)

        page_response = client.get(
            f"https://api.notion.com/v1/pages/{page_id}", headers=headers
        )
        page_response.raise_for_status()
        page = page_response.json()

        rendered_lines = [self._page_title(page)]
        for block in self._fetch_block_children(client, headers, page_id):
            rendered_lines.extend(self._render_block(block, client, headers))

        content = "\n\n".join(line for line in rendered_lines if line.strip())
        return DocumentSource(title=self._page_title(page), content=content)


class RallyConnector(HttpDocumentConnector):
    def matches(self, source_origin: str, source_url: str) -> bool:
        normalized_origin = source_origin.upper()
        return (
            normalized_origin == "RALLY"
            or "rallydev.com" in source_url
            or "rally1.rallydev.com" in source_url
        )

    def _build_headers(self, document_token: Optional[str]) -> dict[str, str]:
        if not document_token or not document_token.strip():
            raise RuntimeError("Rally connector requires a Rally API token.")
        credentials = document_token.strip()
        if ":" not in credentials:
            credentials = f"{credentials}:"
        encoded_credentials = base64.b64encode(credentials.encode("utf-8")).decode(
            "ascii"
        )
        return {
            "Authorization": f"Basic {encoded_credentials}",
            "Accept": "application/json",
            "User-Agent": "Ares-Worker-Librarian/1.0",
        }

    def _infer_collection(self, source_url: str) -> tuple[str, str]:
        parsed = urlparse(source_url)
        candidate = f"{parsed.path}/{parsed.fragment}".lower()
        match = re.search(
            r"(userstory|story|hierarchicalrequirement|defect|task|testcase|feature)[^0-9]*(\d+)",
            candidate,
        )
        if not match:
            raise RuntimeError(
                f"Unable to infer a Rally artifact type from URL: {source_url}"
            )

        artifact_type = match.group(1)
        object_id = match.group(2)
        collection_map = {
            "userstory": "hierarchicalrequirement",
            "story": "hierarchicalrequirement",
            "hierarchicalrequirement": "hierarchicalrequirement",
            "defect": "defect",
            "task": "task",
            "testcase": "testcase",
            "feature": "portfolioitem/feature",
        }
        return collection_map[artifact_type], object_id

    def _strip_html(self, text: str) -> str:
        text = re.sub(r"<br\s*/?>", "\n", text, flags=re.IGNORECASE)
        text = re.sub(r"</p>", "\n\n", text, flags=re.IGNORECASE)
        text = re.sub(r"<[^>]+>", "", text)
        return unescape(text).strip()

    def fetch(
        self,
        client: httpx.Client,
        source_url: str,
        document_token: Optional[str],
    ) -> DocumentSource:
        headers = self._build_headers(document_token)
        collection, object_id = self._infer_collection(source_url)
        parsed = urlparse(source_url)
        api_base = f"{parsed.scheme or 'https'}://{parsed.netloc}/slm/webservice/v2.0"

        response = client.get(
            f"{api_base}/{collection}",
            headers=headers,
            params={
                "query": f"(ObjectID = {object_id})",
                "fetch": "FormattedID,Name,Description,ObjectID",
                "pagesize": 1,
            },
        )
        response.raise_for_status()
        payload = response.json()

        query_result = payload.get("QueryResult", payload)
        results = (
            query_result.get("Results", []) if isinstance(query_result, dict) else []
        )
        if not results:
            raise RuntimeError(f"Rally artifact not found for URL: {source_url}")

        artifact = results[0]
        formatted_id = artifact.get("FormattedID") or f"{collection}:{object_id}"
        name = artifact.get("Name") or source_url
        description = artifact.get("Description") or ""
        content = self._strip_html(str(description))
        if not content:
            content = name

        return DocumentSource(title=f"{formatted_id} {name}".strip(), content=content)


CONNECTORS: tuple[DocumentConnector, ...] = (
    NotionConnector(),
    RallyConnector(),
)


def resolve_document_connector(source_url: str) -> DocumentConnector:
    return resolve_document_connector_by_origin("", source_url)


def resolve_document_connector_by_origin(
    source_origin: str, source_url: str
) -> DocumentConnector:
    for connector in CONNECTORS:
        if connector.matches(source_origin, source_url):
            return connector
    return HttpDocumentConnector()
