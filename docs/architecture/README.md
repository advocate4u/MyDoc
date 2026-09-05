# MyDoc Architecture

## High-level layers

```text
Android UI
   ↓
Application / ViewModel layer
   ↓
Core document model + use cases
   ↓
Editor / File I/O / Rendering abstractions
   ↓
Format-specific engines
```

The UI should not depend directly on a particular document engine. This allows the underlying implementation to evolve without rewriting the Android application.

## First milestone

1. Android project bootstrapping
2. Main document/file browser
3. Android Storage Access Framework integration
4. Open and preview a document
5. Establish the editor abstraction
6. Add DOCX support
7. Add save/export flows

## Security and licensing

Third-party engines will be evaluated independently for license compatibility. Proprietary WPS implementation details will not be copied into MyDoc.
