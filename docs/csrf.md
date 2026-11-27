# CSRF strategy

## What each filter chain accepts

| Chain | Matches | Credentials accepted | Session | CSRF |
|---|---|---|---|---|
| `hardenedApi` (`@Order(1)`, `HardeningConfig`, JWT) | `/api/**` | `Authorization: Bearer <JWT>` only (login itself is a JSON body, `permitAll`) | `STATELESS`, no `Set-Cookie` | **disabled** |
| `pages` (`@Order(2)`, `HardeningConfig`) | `/products/**` | none, public `GET` only | none needed | Spring default (on); nothing can change state here |
| `chain` (Task 4, now `@Order(10)` and limited to `/api/**`, so `hardenedApi` handles `/api/**` first) | `/api/**` | same as `hardenedApi` (never reached) | `STATELESS` | disabled |
| `api` (Task 1, unchanged, no `@Order` so it is evaluated last) | everything else | HTTP Basic, but every rule ends in `denyAll()` | `STATELESS` | disabled (no cookies, Basic header only) |

## Why CSRF is disabled for the Bearer-only `/api/**` chain

A CSRF attack works because the browser **attaches credentials automatically**: a cookie (or cached
Basic credentials) is sent with a request that a malicious page triggers. The `/api/**` chain accepts
neither. The JWT lives in JavaScript memory and is added by our own code in an `Authorization`
header, and a foreign site cannot make the browser add that header. A cross-site request therefore
arrives without credentials and is answered `401`. There is no ambient authority to forge, so a CSRF
token would protect nothing and only add friction for API clients.

This only holds while the chain stays cookie-free. The conditions are enforced in `SecurityConfig`:
`SessionCreationPolicy.STATELESS`, no `formLogin`, no `httpBasic`, and the JWT is never written to a
cookie. CORS (`CorsConfig`) additionally allows exactly one origin, `http://localhost:5173`, and
`allowCredentials(false)`.

## If a same-origin SPA with a session cookie were added

As soon as a browser authenticates with a cookie, CSRF protection must be turned back on for that
chain. The usual SPA setup is a double-submit cookie that JavaScript can read:

```java
http.csrf(csrf -> csrf
    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())   // XSRF-TOKEN cookie, readable by JS
    .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()));           // Spring Security's documented SPA handler
```

The server sets the `XSRF-TOKEN` cookie; the SPA copies its value into the `X-XSRF-TOKEN` header of
every state-changing request (`POST`, `PUT`, `PATCH`, `DELETE`). A third-party site can make the
browser *send* the cookie but cannot *read* it, so it cannot produce the matching header.

```js
function csrfToken() {
  return document.cookie.split('; ')
    .find(c => c.startsWith('XSRF-TOKEN='))?.split('=')[1];
}

await fetch('/api/orders', {
  method: 'POST',
  credentials: 'same-origin',                       // send the session cookie
  headers: {
    'Content-Type': 'application/json',
    'X-XSRF-TOKEN': decodeURIComponent(csrfToken()) // token the server put in the cookie
  },
  body: JSON.stringify({ productId: 1, quantity: 1 })
});
```

Also set the session cookie with `SameSite=Lax` (or `Strict`) as a second layer. `withHttpOnlyFalse()` is
acceptable only for the CSRF cookie, never for the session cookie. That one stays `HttpOnly`.
