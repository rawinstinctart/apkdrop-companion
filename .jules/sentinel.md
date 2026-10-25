## 2024-10-25 - Prevented Information Leakage in AppLibraryController
**Vulnerability:** Exception messages were exposed to users via Toast.makeText.
**Learning:** This can lead to information leakage by showing stack trace and error message directly to the user interface.
**Prevention:** Avoid passing raw Exception messages directly to users; always catch errors and show generic, sanitized error messages.
