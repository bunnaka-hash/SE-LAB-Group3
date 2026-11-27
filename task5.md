# Task 5 – commands to test and finish

Branch: `Jingpo-Task5`. Use two terminals. Every command below uses `localhost:8080` directly, so you can paste
them one by one. Only `TOK` (alice's token) is a variable: set it once in Terminal 2 and keep using the same window.

## Terminal 1: start the server

```bash
cd "/home/jingpo/ITC-I4/Course/Software Engineering/Lab05-Team/SE-LAB-Group3"
export JWT_SECRET=0123456789abcdef0123456789abcdef0123456789abcdef
export SHOP_SEED_ADMIN_PASSWORD='Admin-pass-123'
export SHOP_SEED_CUSTOMER_PASSWORD='Alice-pass-123'
./mvnw -q -Dmaven.test.skip=true spring-boot:run
```

Wait for `Started ShopSecureApplication`. `JWT_SECRET` must be at least 32 characters. `-Dmaven.test.skip=true`
stops Maven from compiling the tests before starting the app.

## Terminal 2: checks

### 0. Get alice's token (once)

```bash
TOK=$(curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' -d '{"username":"alice","password":"Alice-pass-123"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])'); echo "token length: ${#TOK}"
```

Expect a number like 272, not 0.

**Troubleshooting:** if a later check returns `401 ... Bearer token is malformed` (and check 5 shows `[]`), `$TOK`
is empty in this terminal, so the review was never saved. Run the command above again until it prints a number, then
redo checks 4–7. If it prints 0 or errors, restart Terminal 1 with the three `export` lines
(`SHOP_SEED_CUSTOMER_PASSWORD` must be `Alice-pass-123`). Open a new terminal? Set `TOK` again, it is not shared.

### 1. CORS allowed origin

Expect `Access-Control-Allow-Origin: http://localhost:5173` and `Access-Control-Max-Age: 3600`.

```bash
curl -i -X OPTIONS localhost:8080/api/orders -H 'Origin: http://localhost:5173' -H 'Access-Control-Request-Method: POST' -H 'Access-Control-Request-Headers: authorization,content-type'
```

### 2. CORS evil origin

Expect 403 and NO `Access-Control-Allow-Origin` line.

```bash
curl -i -X OPTIONS localhost:8080/api/orders -H 'Origin: http://evil.example' -H 'Access-Control-Request-Method: POST' -H 'Access-Control-Request-Headers: authorization,content-type'
```

### 3. Security headers

Expect 5 lines: nosniff, `X-Frame-Options: DENY`, `Strict-Transport-Security`, CSP with `frame-ancestors 'none'`, Referrer-Policy.

```bash
curl -I localhost:8080/api/products | grep -iE 'x-frame|strict-transport|content-security|referrer|x-content'
```

### 4. Post the XSS payload as alice

Expect 201 and a `Location` header.

```bash
curl -si -X POST localhost:8080/api/reviews -H "Authorization: Bearer $TOK" -H 'Content-Type: application/json' -d '{"productId":2,"stars":1,"text":"<img src=x onerror=\"document.title='"'"'pwned'"'"'\">"}'
```

### 5. JSON API

Expect `Content-Type: application/json`, `X-Content-Type-Options: nosniff`, text stored unchanged.

```bash
curl -si localhost:8080/api/products/2/reviews | grep -iE 'content-type|x-content-type|^\['
```

### 6. HTML page (th:text)

Expect `&lt;img ... &gt;` (escaped), not a real `<img`.

```bash
curl -s localhost:8080/products/2/reviews.html | grep '<p'
```

### 7. Validation and auth

Expect a 400 body for the first command and `HTTP/1.1 401` for the second.

```bash
curl -s -X POST localhost:8080/api/reviews -H "Authorization: Bearer $TOK" -H 'Content-Type: application/json' -d '{"productId":2,"stars":9,"text":""}'
```

```bash
curl -si -X POST localhost:8080/api/reviews -H 'Content-Type: application/json' -d '{"productId":2,"stars":3,"text":"x"}' | head -1
```

### 8. Security log lines

Run these four, then look in Terminal 1 (Spring Security TRACE output is verbose, so look for `SECURITY`).

```bash
curl -s -o /dev/null -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' -d '{"username":"alice","password":"wrong"}'
curl -s -o /dev/null -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' -d '{"username":"alice","password":"Alice-pass-123"}'
curl -s -o /dev/null -X POST localhost:8080/api/orders -H "Authorization: Bearer $TOK" -H 'Content-Type: application/json' -d '{"productId":1,"quantity":1}'
curl -si -X POST localhost:8080/api/orders/1/ship -H "Authorization: Bearer $TOK" | grep -iE '^HTTP|x-request-id'
```

Expect a 403, and in Terminal 1 these lines, each with `rid=`:

```text
WARN  rid=… SECURITY : login.failed user=alice reason=BadCredentialsException
INFO  rid=… SECURITY : login.ok user=alice type=UsernamePasswordAuthenticationToken
WARN  rid=… SECURITY : access.denied user=alice object=OrderService.ship(1)
```

The `X-Request-Id` of the 403 matches the `rid` of the `access.denied` line.

### 9. Secrets

Every value hit must be `${...}` or a test-only value in a test file. `.env` must be printed back (it is ignored).

```bash
git grep -n -i -E "password|secret" -- '*.yml' '*.properties' '*.java'
git check-ignore .env
```

## Terminal 1 again: fail fast

Stop the server with Ctrl+C, then:

```bash
JWT_SECRET= ./mvnw -q -Dmaven.test.skip=true spring-boot:run
```

Expect `APPLICATION FAILED TO START` and `Binding to target edu.itc.shop.config.SecurityProps failed`.
(`unset SHOP_SEED_ADMIN_PASSWORD` instead gives `Could not resolve placeholder 'SHOP_SEED_ADMIN_PASSWORD'`.)

## Unit tests

```bash
./mvnw -q verify
```

`HardeningTest` (5 tests: CORS, headers, stored XSS, validation) should pass. `OrderControllerSecurityTest` (Task 3)
currently fails because it does not provide `JwtAuthenticationConverter` / `ProblemAuthEntryPoint` for the Task 4 JWT
chain. That is not caused by Task 5, so `verify` will not exit 0 until that test is fixed.

## Still manual: `th:utext` counter-example (branch that is never merged)

```bash
git switch -c task5-utext-demo
```

1. In `src/main/resources/templates/reviews.html` change `th:text="${r.text}"` to `th:utext="${r.text}"`.
2. The CSP would block the inline `onerror`. To show the real effect, temporarily remove the
   `.contentSecurityPolicy(...)` call in `HardeningConfig.hardenHeaders`.
3. Run the app, post the payload as alice (check 4), open `http://localhost:8080/products/2/reviews.html` in a browser.
   Expect a broken image and the tab title `pwned`. Take a screenshot.
4. Take a second screenshot of the same page on `Jingpo-Task5` (payload shown as literal text).
5. Save both (for example `docs/xss-th-text.png`, `docs/xss-th-utext.png`) and add them to `security-session.md`,
   replacing the line "`th:utext` counter-example: *not yet recorded*".
6. Drop the demo edits and go back. Never merge the demo branch:

```bash
git restore .
git switch Jingpo-Task5
git branch -D task5-utext-demo
```

Commit the screenshots and the `security-session.md` change on `Jingpo-Task5`.

## Finish: commit, tag, clean-clone check

```bash
git add -A && git status
git commit -m "Task 5: CORS, headers, XSS demo, secrets, security logging"
git tag task-5
git push origin Jingpo-Task5 --tags
```

`.env` must NOT be listed by `git status`.

Clean clone with only the variables from `.env.example` set (needs the Task 3 test fixed to exit 0):

```bash
rm -rf /tmp/shop-clean && git clone --branch Jingpo-Task5 "$(git remote get-url origin)" /tmp/shop-clean
cd /tmp/shop-clean
env -i PATH="$PATH" HOME="$HOME" \
  JWT_SECRET=0123456789abcdef0123456789abcdef0123456789abcdef \
  SHOP_SEED_ADMIN_PASSWORD='Admin-pass-123' SHOP_SEED_CUSTOMER_PASSWORD='Alice-pass-123' \
  ./mvnw -q verify
echo "exit code: $?"
```

## Acceptance checklist

- [ ] `docs/csrf.md` explains the per-chain CSRF decision
- [ ] CORS allows only `http://localhost:5173`; checks 1–2 recorded in `security-session.md`
- [ ] Check 3 shows nosniff, `X-Frame-Options: DENY`, HSTS, CSP `frame-ancestors 'none'`, Referrer-Policy
- [ ] Checks 4–7: payload stored unchanged, size-validated, harmless in JSON and in the `th:text` page
- [ ] `th:utext` counter-example documented with screenshot (unmerged branch)
- [ ] Check 9: no literal secret; `.env.example` has no values
- [ ] Fail-fast: app refuses to start without `JWT_SECRET`
- [ ] Check 8: failed login, login ok and access denied logged with request ids
- [ ] Tag `task-5` pushed; clean-clone `./mvnw -q verify` exits 0
