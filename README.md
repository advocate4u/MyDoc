# MyDoc

A WPS-style Android document application focused on creating, opening, editing, rendering, and managing office documents.

## Vision

MyDoc will provide a clean Android-first document experience with support for common office formats, document rendering, editing, file management, and future AI-assisted features.

## Project principles

- Android-first and offline-friendly
- Modular architecture
- Keep document-format engines isolated from the UI
- Prefer open-source components with compatible licenses
- Do not copy proprietary WPS code, assets, or implementation details

## Planned capabilities

- Document browser and recent files
- DOC/DOCX viewing and editing
- PDF viewing
- Spreadsheet and presentation support as separate modules
- Import/export and file sharing
- Search and document navigation
- Dark mode
- Future AI writing and document assistance

## Initial architecture

```text
MyDoc
├── android/       Android application and UI
├── core/          Shared domain models and utilities
├── editor/        Editing abstractions and editor features
├── fileio/        File access and document import/export
├── rendering/     Rendering abstractions
├── docs/          Architecture, formats, and roadmap
└── scripts/       Development/build helpers
```

## Status

🚧 Initial project setup.
