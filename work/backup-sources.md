# Android document integration

Uses Android Storage Access Framework ACTION_CREATE_DOCUMENT and ACTION_OPEN_DOCUMENT with CATEGORY_OPENABLE, then ContentResolver streams. No broad storage permission is requested. The user chooses the provider/location; app itself has no network permission.

Primary reference: https://developer.android.com/training/data-storage/shared/documents-files

Tests cover actual streams using app-owned cache file URIs, background completion, review/cancel, duplicate-safe merge and invalid-file preservation. Document-provider UI is checked separately; third-party/cloud providers are not all covered.
