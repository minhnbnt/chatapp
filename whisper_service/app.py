import logging
import re
import subprocess
import tempfile
from pathlib import Path

from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from groq import Groq
from pydantic import BaseModel

LOGGER = logging.getLogger("whisper-service")
logging.basicConfig(level=logging.INFO)

import os


def get_env(key: str, default: str | None = None) -> str | None:
    try:
        from google.colab import userdata

        value = userdata.get(key)
        if value is None or value == "":
            return default
        return value
    except Exception:
        return os.getenv(key, default)


GROQ_API_KEY = get_env("GROQ_API_KEY", "")
GROQ_MODEL = get_env("GROQ_WHISPER_MODEL", "whisper-large-v3-turbo").strip() or "whisper-large-v3-turbo"

DEFAULT_LANGUAGE = (
    (get_env("WHISPER_DEFAULT_LANGUAGE") or get_env("WHISPER_LANGUAGE") or "auto")
    .strip()
    .lower()
)
MAX_AUDIO_SIZE_BYTES = int(get_env("WHISPER_MAX_AUDIO_SIZE_BYTES", "12582912"))

SUPPORTED_LANGUAGE_CODES = {"vi", "en", "ja"}

LANGUAGE_ALIASES = {
    "vi-vn": "vi",
    "vietnamese": "vi",
    "en-us": "en",
    "en-gb": "en",
    "english": "en",
    "ja-jp": "ja",
    "jp": "ja",
    "japanese": "ja",
}

MULTILINGUAL_INITIAL_PROMPT = (
    "This is a chat voice message. "
    "The language may be Vietnamese, English, or Japanese. "
    "Transcribe exactly in the original language."
)

LANGUAGE_INITIAL_PROMPTS = {
    "vi": "This is a Vietnamese chat voice message. Transcribe exactly with proper diacritics.",
    "en": "This is an English chat voice message. Transcribe exactly as spoken.",
    "ja": "This is a Japanese chat voice message. Transcribe exactly as spoken.",
}

_AUDIO_NAME_RE = re.compile(r"[^a-zA-Z0-9._-]")

app = FastAPI(title="Whisper Speech To Text (Groq)", version="2.0.0")
_groq_client: Groq | None = None


class SpeechToTextResponse(BaseModel):
    text: str


class HealthResponse(BaseModel):
    status: str
    model: str


def _get_groq_client() -> Groq:
    global _groq_client
    if _groq_client is not None:
        return _groq_client

    api_key = GROQ_API_KEY
    if not api_key:
        raise HTTPException(
            status_code=500,
            detail="GROQ_API_KEY environment variable is not set",
        )

    _groq_client = Groq(api_key=api_key)
    LOGGER.info("Groq client initialized (model: %s)", GROQ_MODEL)
    return _groq_client


@app.on_event("startup")
def startup() -> None:
    if GROQ_API_KEY:
        _get_groq_client()
        LOGGER.info("Groq Whisper service ready (model: %s)", GROQ_MODEL)
    else:
        LOGGER.warning("GROQ_API_KEY is not set — transcription will fail until it is configured")


@app.get("/health", response_model=HealthResponse)
def health() -> HealthResponse:
    return HealthResponse(status="ok", model=GROQ_MODEL)


@app.post("/speech-to-text", response_model=SpeechToTextResponse)
async def speech_to_text(
    audio: UploadFile = File(...),
    language: str = Form(DEFAULT_LANGUAGE),
    prompt: str | None = Form(None),
) -> SpeechToTextResponse:
    if audio.filename is None or audio.filename.strip() == "":
        raise HTTPException(status_code=400, detail="audio filename is required")

    safe_name = _safe_filename(audio.filename)
    normalized_language = _normalize_language(language)
    normalized_prompt = _normalize_prompt(prompt)

    with tempfile.TemporaryDirectory(prefix="whisper-stt-") as tmp:
        source_path = Path(tmp) / safe_name
        total_size = 0

        with source_path.open("wb") as output:
            while True:
                chunk = await audio.read(1024 * 1024)
                if not chunk:
                    break

                total_size += len(chunk)
                if total_size > MAX_AUDIO_SIZE_BYTES:
                    raise HTTPException(
                        status_code=413,
                        detail="audio file is too large",
                    )

                output.write(chunk)

        await audio.close()

        if total_size == 0:
            raise HTTPException(status_code=400, detail="audio file is empty")

        # Convert to WAV for consistent input to Groq API
        normalized_audio = Path(tmp) / "normalized.wav"
        _convert_to_wav(source_path, normalized_audio)

        # Build the initial prompt
        initial_prompt = normalized_prompt
        if not initial_prompt:
            if normalized_language is None:
                initial_prompt = MULTILINGUAL_INITIAL_PROMPT
            elif normalized_language in LANGUAGE_INITIAL_PROMPTS:
                initial_prompt = LANGUAGE_INITIAL_PROMPTS[normalized_language]

        try:
            client = _get_groq_client()

            with open(normalized_audio, "rb") as audio_file:
                transcription_kwargs = {
                    "file": ("normalized.wav", audio_file.read()),
                    "model": GROQ_MODEL,
                    "temperature": 0,
                    "response_format": "verbose_json",
                }

                if normalized_language is not None:
                    transcription_kwargs["language"] = normalized_language

                if initial_prompt:
                    transcription_kwargs["prompt"] = initial_prompt

                transcription = client.audio.transcriptions.create(**transcription_kwargs)

        except HTTPException:
            raise
        except Exception as exc:
            LOGGER.exception("Groq Whisper transcription failed")
            raise HTTPException(
                status_code=502,
                detail=f"whisper transcription failed: {exc}",
            )

        text = (transcription.text or "").strip()
        return SpeechToTextResponse(text=text)


def _convert_to_wav(source_path: Path, target_path: Path) -> None:
    command = [
        "ffmpeg",
        "-y",
        "-i",
        str(source_path),
        "-ac",
        "1",
        "-ar",
        "16000",
        "-vn",
        str(target_path),
    ]

    process = subprocess.run(
        command,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        check=False,
    )

    if process.returncode != 0:
        LOGGER.error("ffmpeg conversion failed: %s", process.stderr)
        raise HTTPException(status_code=422, detail="unable to decode audio input")


def _safe_filename(filename: str) -> str:
    candidate = _AUDIO_NAME_RE.sub("_", filename)
    candidate = candidate.strip("._")
    if not candidate:
        candidate = "audio_upload.webm"

    if "." not in candidate:
        candidate += ".webm"

    return candidate


def _normalize_language(language: str | None) -> str | None:
    value = _canonicalize_language(language)
    if not value:
        value = _canonicalize_language(DEFAULT_LANGUAGE)

    if not value:
        value = "auto"

    if value == "auto":
        return None

    if value not in SUPPORTED_LANGUAGE_CODES:
        raise HTTPException(
            status_code=400,
            detail="invalid language: supported values are auto, vi, en, ja",
        )

    return value


def _canonicalize_language(language: str | None) -> str:
    value = (language or "").strip().lower().replace("_", "-")
    if not value:
        return ""

    return LANGUAGE_ALIASES.get(value, value)


def _normalize_prompt(prompt: str | None) -> str:
    if prompt is None:
        return ""

    value = prompt.strip()
    if len(value) > 240:
        value = value[:240]

    return value
