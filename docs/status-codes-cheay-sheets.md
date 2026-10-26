# HTTP Status Codes Cheat Sheet (REST APIs)

## The families

| Range | Meaning | Who's at fault |
|-------|---------|----------------|
| 1xx | Informational | n/a (rarely used in APIs) |
| 2xx | Success | nobody |
| 3xx | Redirection | n/a |
| 4xx | Client error | the caller |
| 5xx | Server error | you / the server |

---

## 2xx: Success

| Code | Use when | Typical example |
|------|----------|-----------------|
| **200 OK** | Request succeeded and you return a body | `GET /users/1`, `PUT` returning the updated resource |
| **201 Created** | A new resource was created | `POST /users` (add `Location` header with the new URL) |
| **202 Accepted** | Accepted but processed asynchronously | Queued job, report generation |
| **204 No Content** | Succeeded, nothing to return | `DELETE /users/1`, `PUT` with no body |

## 3xx: Redirection

| Code | Use when |
|------|----------|
| **301 Moved Permanently** | Resource URL changed forever (clients/SEO cache it) |
| **302 Found** | Temporary redirect |
| **304 Not Modified** | Client's cached copy is still valid (`ETag` / `If-None-Match`) |
| **307 / 308** | Like 302 / 301 but the HTTP method must not change |

## 4xx: Client errors

| Code | Use when | Example |
|------|----------|---------|
| **400 Bad Request** | Malformed request: bad JSON, wrong types, missing params | Invalid JSON body |
| **401 Unauthorized** | Not authenticated (no or invalid credentials/token) | Missing or expired JWT |
| **403 Forbidden** | Authenticated but not allowed | User tries to access admin endpoint |
| **404 Not Found** | Resource doesn't exist | `GET /users/999` |
| **405 Method Not Allowed** | Endpoint exists, method doesn't | `DELETE /login` |
| **409 Conflict** | Request conflicts with current state | Duplicate email, version/optimistic-lock conflict, duplicate idempotency key with different payload |
| **410 Gone** | Resource existed but was permanently removed | Deleted and never coming back |
| **415 Unsupported Media Type** | Wrong `Content-Type` | Sent XML, API accepts JSON only |
| **422 Unprocessable Entity** | Syntax is valid but business/validation rules fail | Email format invalid, age is negative |
| **429 Too Many Requests** | Rate limit hit (add `Retry-After`) | Too many login attempts |

## 5xx: Server errors

| Code | Use when |
|------|----------|
| **500 Internal Server Error** | Unexpected bug or unhandled exception |
| **502 Bad Gateway** | Upstream service returned an invalid response |
| **503 Service Unavailable** | Down for maintenance or overloaded (add `Retry-After`) |
| **504 Gateway Timeout** | Upstream service didn't respond in time |

---

## Quick decision guide

**Reading data**
- Found → `200`
- Doesn't exist → `404`
- Not logged in → `401`
- Logged in but no permission → `403`

**Creating (`POST`)**
- Created → `201` + `Location` header
- Invalid input → `400` or `422`
- Already exists → `409`
- Async processing → `202`

**Updating (`PUT` / `PATCH`)**
- Updated, return resource → `200`
- Updated, no body → `204`
- Not found → `404`
- Stale version / conflict → `409`

**Deleting (`DELETE`)**
- Deleted → `204`
- Already gone / never existed → `404` (or `204` if you want idempotent behavior)

---

## Common confusions

| Confusion | Rule of thumb |
|-----------|---------------|
| **401 vs 403** | 401 = "who are you?" (identity unknown). 403 = "I know who you are, and no." |
| **400 vs 422** | 400 = can't even parse/understand the request. 422 = understood it, but the data is invalid. (Many teams just use 400 for both. Pick one and be consistent.) |
| **404 vs 403** | Use 404 to hide that a resource exists from users who shouldn't know about it. |
| **200 vs 201 vs 204** | 200 = success with body, 201 = created, 204 = success without body. |
| **500 vs 4xx** | If the client can fix it by changing the request, it's 4xx. If not, it's 5xx. |

## Best practices

- Never return `200` with an error inside the body. Use the right status code.
- Never leak stack traces on `500`. Log them server-side, return a generic message.
- Use a consistent error body, e.g. `{ "code": "...", "message": "...", "details": [...] }`.
- Add `Retry-After` on `429` and `503`.
- Make `GET`, `PUT`, `DELETE` idempotent; use idempotency keys for `POST` on payments.
- Map exceptions to status codes in one place (e.g. `@ControllerAdvice` in Spring Boot).

## Codes to memorize first

`200` `201` `204` `400` `401` `403` `404` `409` `422` `429` `500` `503`


____
____
____
____
____
____
____
____
____
____
____
____
____
____



## MockMvc HTTP Status Matchers

Use `.andExpect(status()...)` to verify the HTTP status returned by an endpoint.

### Success — 2xx

| MockMvc matcher | Code | Use case |
|---|---:|---|
| `status().isOk()` | 200 | Request succeeded |
| `status().isCreated()` | 201 | Resource created, e.g. `POST /accounts` |
| `status().isAccepted()` | 202 | Request accepted for asynchronous processing |
| `status().isNoContent()` | 204 | Request succeeded with no response body |

### Client errors — 4xx

| MockMvc matcher | Code | Use case |
|---|---:|---|
| `status().isBadRequest()` | 400 | Invalid request, malformed JSON, validation failure |
| `status().isUnauthorized()` | 401 | Missing or invalid authentication |
| `status().isForbidden()` | 403 | Authenticated but not authorized |
| `status().isNotFound()` | 404 | Resource or route not found |
| `status().isMethodNotAllowed()` | 405 | HTTP method not supported by the endpoint |
| `status().isConflict()` | 409 | Conflict with existing state, e.g. account-name conflict or already-reversed transaction |
| `status().isUnprocessableEntity()` | 422 | Request understood but rejected by a business rule, e.g. idempotency key reused with different content |
| `status().isTooManyRequests()` | 429 | Rate limit exceeded |

### Server errors — 5xx

| MockMvc matcher | Code | Use case |
|---|---:|---|
| `status().isInternalServerError()` | 500 | Unexpected server error |
| `status().isServiceUnavailable()` | 503 | Service temporarily unavailable |
| `status().isBadGateway()` | 502 | Server received an invalid response from an upstream server |
| `status().isGatewayTimeout()` | 504 | Upstream server did not respond in time |

### General status checks

| MockMvc matcher | Meaning |
|---|---|
| `status().is2xxSuccessful()` | Any successful 2xx status |
| `status().is3xxRedirection()` | Any 3xx redirect status |
| `status().is4xxClientError()` | Any 4xx client-error status |
| `status().is5xxServerError()` | Any 5xx server-error status |
| `status().is(expectedCode)` | Exact numeric status code |

### Example

```java
mockMvc.perform(get("/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
```

### Guidelines

- Prefer specific matchers such as `isCreated()` over broad matchers such as `is2xxSuccessful()`.
- The application determines the status code; `MockMvc` verifies it.
- Use status codes consistently with your API contract and exception handler.
- Not every status needs a dedicated test for every endpoint. Focus on meaningful success and failure scenarios.