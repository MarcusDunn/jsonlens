# JSON Patch tests

`tests.json` and `spec_tests.json` come from the JSON Patch test suite,
<https://github.com/json-patch/json-patch-tests>, commit `2a928f9044aad35c74e2788d498bcf2c6b91adea`
(version 1.1.0).

Copyright 2014 The Authors. Licensed under the Apache License, Version 2.0. You may obtain a copy
of the License at <http://www.apache.org/licenses/LICENSE-2.0>. The files are not changed.

The tests of the `patch` module run each record. A record has `doc`, `patch`, and `expected` (the
result) or `error` (the patch must fail). A record with `disabled` is skipped.
