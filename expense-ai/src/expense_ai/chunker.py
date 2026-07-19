from __future__ import annotations

from langchain_core.documents import Document
from langchain_text_splitters import RecursiveCharacterTextSplitter

DEFAULT_CHUNK_SIZE = 900
DEFAULT_OVERLAP = 150


def make_splitter(
    chunk_size: int = DEFAULT_CHUNK_SIZE,
    overlap: int = DEFAULT_OVERLAP,
) -> RecursiveCharacterTextSplitter:
    if not (0 <= overlap < chunk_size / 2):
        raise ValueError(
            f"overlap must satisfy 0 <= overlap < chunk_size/2, "
            f"got overlap={overlap}, chunk_size={chunk_size}"
        )
    return RecursiveCharacterTextSplitter(
        chunk_size=chunk_size,
        chunk_overlap=overlap,
        separators=["\n\n", "\n", ". ", " ", ""],
    )


def chunk_docs(docs: list[Document], chunk_size: int = DEFAULT_CHUNK_SIZE) -> list[Document]:
    splitter = make_splitter(chunk_size=chunk_size)
    result: list[Document] = []
    for doc in docs:
        doc_id = str(doc.metadata.get("doc_id", "doc-synth-unknown"))
        chunks = splitter.split_documents([doc])
        for i, chunk in enumerate(chunks):
            chunk.metadata["chunk_id"] = f"chunk-{doc_id}-p{i}"
            chunk.metadata["chunk_ordinal"] = i
            result.append(chunk)
    return result
