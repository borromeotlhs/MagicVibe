#!/usr/bin/env python3
"""List available Teamwork Cloud (TWC) projects from a server URL.

Credential JSON format (one of the following):

1) Bearer token:
{
  "bearer_token": "<token>"
}

2) Basic auth:
{
  "username": "alice",
  "password": "secret"
}

Optional keys:
- "verify_ssl": true|false (default: true)
- "headers": {"X-...": "..."}
"""

from __future__ import annotations

import argparse
import base64
import json
import ssl
import sys
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.parse import urljoin
from urllib.request import Request, urlopen

DEFAULT_ENDPOINTS = (
    "/api/projects",
    "/osmc/projects",
    "/projects",
)


def _normalize_base_url(raw_url: str) -> str:
    base = raw_url.strip()
    if not base:
        raise ValueError("Server URL cannot be empty.")
    if not base.startswith(("http://", "https://")):
        base = f"https://{base}"
    return base.rstrip("/") + "/"


def _load_credentials(path: Path) -> dict[str, Any]:
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError as exc:
        raise ValueError(f"Credential file not found: {path}") from exc
    except json.JSONDecodeError as exc:
        raise ValueError(f"Credential file is not valid JSON: {path} ({exc})") from exc

    if not isinstance(data, dict):
        raise ValueError("Credential JSON must be an object.")
    return data


def _auth_headers(creds: dict[str, Any]) -> dict[str, str]:
    headers: dict[str, str] = {"Accept": "application/json"}

    custom_headers = creds.get("headers")
    if custom_headers is not None:
        if not isinstance(custom_headers, dict) or not all(
            isinstance(k, str) and isinstance(v, str) for k, v in custom_headers.items()
        ):
            raise ValueError('"headers" must be a JSON object of string:string pairs.')
        headers.update(custom_headers)

    bearer_token = creds.get("bearer_token")
    username = creds.get("username")
    password = creds.get("password")

    if isinstance(bearer_token, str) and bearer_token.strip():
        headers["Authorization"] = f"Bearer {bearer_token.strip()}"
        return headers

    if isinstance(username, str) and isinstance(password, str):
        token = base64.b64encode(f"{username}:{password}".encode("utf-8")).decode("ascii")
        headers["Authorization"] = f"Basic {token}"
        return headers

    raise ValueError(
        'Credentials must include either "bearer_token" or both "username" and "password".'
    )


def _extract_projects(payload: Any) -> list[dict[str, Any]]:
    if isinstance(payload, list):
        return [item for item in payload if isinstance(item, dict)]

    if not isinstance(payload, dict):
        return []

    for key in ("projects", "data", "items", "content", "results"):
        value = payload.get(key)
        if isinstance(value, list):
            return [item for item in value if isinstance(item, dict)]

    if all(k in payload for k in ("id", "name")):
        return [payload]

    return []


def _project_display(project: dict[str, Any]) -> str:
    name = project.get("name") or project.get("title") or "<unnamed>"
    project_id = project.get("id") or project.get("projectId") or project.get("uuid") or "<no-id>"
    owner = project.get("owner") or project.get("organization") or project.get("namespace")

    if owner:
        return f"- {name} (id={project_id}, owner={owner})"
    return f"- {name} (id={project_id})"


def _request_json(url: str, headers: dict[str, str], context: ssl.SSLContext | None) -> Any:
    req = Request(url, headers=headers, method="GET")
    with urlopen(req, context=context, timeout=30) as response:
        raw = response.read().decode("utf-8")
    return json.loads(raw)


def list_projects(server_url: str, credentials_path: Path, endpoints: tuple[str, ...]) -> int:
    base_url = _normalize_base_url(server_url)
    creds = _load_credentials(credentials_path)
    headers = _auth_headers(creds)

    verify_ssl = bool(creds.get("verify_ssl", True))
    ssl_context = None if verify_ssl else ssl._create_unverified_context()

    errors: list[str] = []
    for endpoint in endpoints:
        url = urljoin(base_url, endpoint.lstrip("/"))
        try:
            payload = _request_json(url, headers, ssl_context)
            projects = _extract_projects(payload)
            if projects:
                print(f"Server: {base_url.rstrip('/')}")
                print(f"Endpoint: {endpoint}")
                print(f"Projects ({len(projects)}):")
                for project in projects:
                    print(_project_display(project))
                return 0

            errors.append(f"{endpoint}: request succeeded but no projects were found in response JSON.")
        except HTTPError as exc:
            detail = exc.read().decode("utf-8", errors="replace") if exc.fp else ""
            errors.append(f"{endpoint}: HTTP {exc.code} {exc.reason}. {detail}".strip())
        except URLError as exc:
            errors.append(f"{endpoint}: connection error: {exc.reason}")
        except json.JSONDecodeError as exc:
            errors.append(f"{endpoint}: response was not valid JSON ({exc})")

    print("Failed to list projects from all known endpoints.", file=sys.stderr)
    for msg in errors:
        print(f"  * {msg}", file=sys.stderr)
    return 1


def main() -> int:
    parser = argparse.ArgumentParser(
        description="List available Teamwork Cloud projects from a server URL and credentials JSON file."
    )
    parser.add_argument("server_url", help="TWC server URL, e.g. https://twc.example.com")
    parser.add_argument("credentials_file", help="Path to JSON credentials file")
    parser.add_argument(
        "--endpoint",
        action="append",
        default=[],
        help=(
            "Project listing endpoint path (may be provided multiple times). "
            f"Defaults: {', '.join(DEFAULT_ENDPOINTS)}"
        ),
    )

    args = parser.parse_args()

    endpoints = tuple(args.endpoint) if args.endpoint else DEFAULT_ENDPOINTS
    return list_projects(args.server_url, Path(args.credentials_file), endpoints)


if __name__ == "__main__":
    raise SystemExit(main())
