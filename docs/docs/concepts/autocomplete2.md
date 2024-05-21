# Autocomplete suggestions

Nixiesearch uses an ES-like approach to suggestions: you mark a field in your [index mapping](todo) as a `suggest=true`
and it becomes possible to send a `_suggest` query requests. 