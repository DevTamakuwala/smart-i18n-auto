<p align="center">
  <img src="https://img.shields.io/badge/Spring%20Boot-4.0.3-brightgreen?style=for-the-badge&logo=springboot" alt="Spring Boot 4.0.3"/>
  <img src="https://img.shields.io/badge/Java-21+-blue?style=for-the-badge&logo=openjdk" alt="Java 21+"/>
  <img src="https://img.shields.io/badge/License-MIT-yellow?style=for-the-badge" alt="MIT License"/>
  <img src="https://img.shields.io/badge/Build-Maven-C71A36?style=for-the-badge&logo=apachemaven" alt="Maven"/>
</p>

# 🌍 smart-i18n-auto

**Plug-and-play automatic internationalization middleware for Spring Boot 4.**

Drop in one dependency, set one API key, and every `@AutoTranslate` endpoint instantly returns responses in the language requested by the client — no message bundles, no resource files, no manual wiring.

```
Client: Accept-Language: fr
Server: { "greeting": "Bonjour le monde" }  ← was "Hello World" in code
```

---

## 📖 Table of Contents

- [Overview](#-overview)
- [Features](#-features)
- [Quick Start](#-quick-start)
- [Installation](#-installation)
- [Maven Central](#-maven-central)
- [Configuration](#-configuration)
  - [Google Cloud Translation](#google-cloud-translation-api-v2)
  - [Google Gemini](#google-gemini-api)
  - [OpenAI](#openai-api)
  - [Provider Selection](#provider-selection)
  - [Safety Guardrails](#safety-guardrails)
- [Architecture](#-architecture)
- [Annotations](#-annotations)
- [Example Usage](#-example-usage)
- [cURL Examples](#-curl-examples)
- [Performance](#-performance)
- [Security](#-security)
- [Testing Locally](#-testing-locally)
- [Release Checklist](#-release-checklist)
- [Roadmap](#-roadmap)
- [Contributing](#-contributing)
- [License](#-license)

---

## 🔭 Overview

`smart-i18n-auto` is a **zero-configuration Spring Boot starter** that intercepts HTTP responses (and optionally requests) and automatically translates string content based on the `Accept-Language` header.

It is designed for teams who:

- Want **instant multi-language support** without maintaining `.properties` files per locale
- Need **runtime translation** powered by modern LLMs or translation APIs
- Require a **drop-in library** that works with existing controllers and DTOs
- Value **production-grade safeguards** — caching, cost limits, depth limits, API key security

### How It Works

```
Client Request                          Your Controller
     │                                       │
     │  Accept-Language: hi                  │  return ProductDto("Wireless Mouse", "Great for gaming")
     │                                       │
     ▼                                       ▼
┌─────────────────────────────────────────────────────┐
│                smart-i18n-auto                      │
│                                                     │
│  1. Detect target language from header (hi)         │
│  2. Traverse DTO → collect translatable strings     │
│  3. Deduplicate → check Caffeine cache              │
│  4. Batch-translate uncached via provider API       │
│  5. Cache results → write back to DTO               │
└─────────────────────────────────────────────────────┘
     │
     ▼
Client Response
  { "name": "वायरलेस माउस", "description": "गेमिंग के लिए बढ़िया" }
```

---

## ✨ Features

| Category | Feature |
|----------|---------|
| **Zero Config** | Auto-configured via `@AutoConfiguration` — just add the dependency and an API key |
| **Multi-Provider** | Google Cloud Translation v2, Google Gemini, OpenAI Chat Completions |
| **Auto-Detection** | Provider auto-selected based on which API key is configured |
| **Fallback Chain** | Optional hybrid mode — if one provider fails, the next is tried |
| **Aggressive Caching** | Caffeine cache with configurable TTL and max size |
| **Batch Translation** | Collects all strings from a DTO and translates in a single API call |
| **Deduplication** | Identical strings sent to the API only once, results fanned out |
| **Smart Filtering** | Auto-skips IDs, numbers, UUIDs, URLs, emails, enums, dates, short tokens |
| **Annotation-Driven** | `@AutoTranslate` on controllers/methods, `@SkipTranslation` on fields |
| **Deep Traversal** | Recursively walks String, List, Map, nested DTOs, arrays |
| **Circular Ref Safe** | `IdentityHashMap`-based visited tracking prevents infinite loops |
| **Cost Protection** | Configurable max strings/request, max text length, max traversal depth |
| **Response Buffer Limit** | WebClient memory cap prevents OOM from malformed API responses |
| **AOP Support** | `@AutoTranslate` works on service-layer methods too, not just controllers |
| **Request Translation** | Optional inbound request body translation (normalize to English) |
| **Jakarta Compatible** | Full Jakarta namespace — Spring Boot 4 / Spring Framework 7 ready |
| **API Key Security** | Keys sent via HTTP headers (not URLs), masked in `toString()` / logs |
| **No Embedded Server** | Pure library JAR — no main class, no `spring-boot-starter-web` dependency |

---

## 🚀 Quick Start

**1. Add the dependency:**

```xml
<dependency>
    <groupId>in.devtamakuwala</groupId>
    <artifactId>smart-i18n-auto</artifactId>
    <version>1.0.0</version>
</dependency>
```

**2. Set an API key** (pick any one provider):

```properties
smart.i18n.gemini.api-key=YOUR_GEMINI_API_KEY
```

**3. Annotate your controller:**

```java
@RestController
@AutoTranslate
public class ProductController {

    @GetMapping("/products/{id}")
    public ProductDto getProduct(@PathVariable Long id) {
        return new ProductDto("Wireless Mouse", "Great for gaming", 29.99);
    }
}
```

**4. Call with `Accept-Language`:**

```bash
curl -H "Accept-Language: fr" http://localhost:8080/products/1
```

**Response:**
```json
{
  "name": "Souris sans fil",
  "description": "Idéal pour le gaming",
  "price": 29.99
}
```

That's it. No message bundles. No `LocaleResolver`. No boilerplate.

---

## 📦 Installation

### Maven

```xml
<dependency>
    <groupId>in.devtamakuwala</groupId>
    <artifactId>smart-i18n-auto</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle

```groovy
implementation 'in.devtamakuwala:smart-i18n-auto:1.0.0'
```

> Use the latest released version when a newer one is available on Maven Central.

---

## 🏛 Maven Central

Coordinates:

```xml
<dependency>
    <groupId>in.devtamakuwala</groupId>
    <artifactId>smart-i18n-auto</artifactId>
    <version>1.0.0</version>
</dependency>
```

- GroupId namespace: `in.devtamakuwala`
- ArtifactId: `smart-i18n-auto`
- Repository: `https://github.com/DevTamakuwala/smart-i18n-auto`

---

## ⚙️ Configuration

All properties are prefixed with `smart.i18n`.

### Core Properties

```properties
# Enable/disable the middleware (default: true)
smart.i18n.enabled=true

# Base language of your application content (default: en)
smart.i18n.source-locale=en

# Fallback target language when no header is detected (default: en)
smart.i18n.default-target-locale=en

# Optional: custom header name instead of Accept-Language
smart.i18n.header-name=X-Target-Language

# Optional: query parameter for language
smart.i18n.query-param=lang

# Translate incoming request bodies to English (default: false)
smart.i18n.translate-request-body=false
```

### Google Cloud Translation API v2

```properties
smart.i18n.google-cloud.api-key=AIzaSy...YOUR_KEY
```

> Uses `X-Goog-Api-Key` header authentication. Supports native batch translation via the `q` array parameter. Best for high-volume, cost-effective translation.

### Google Gemini API

```properties
smart.i18n.gemini.api-key=AIzaSy...YOUR_KEY
smart.i18n.gemini.model=gemini-2.0-flash
```

> Uses `X-Goog-Api-Key` header authentication. Structured prompt → JSON array response. Best for nuanced/contextual translations. Default model: `gemini-2.0-flash`.

### OpenAI API

```properties
smart.i18n.openai.api-key=sk-proj-...YOUR_KEY
smart.i18n.openai.model=gpt-4o-mini
```

> Uses `Authorization: Bearer` header. System + user prompt → JSON array response. `temperature=0.1` for consistent translations. Default model: `gpt-4o-mini`.

### Provider Selection

```properties
# Explicit provider selection (optional — auto-detected by default)
smart.i18n.provider.active=gemini

# Enable fallback chain: if primary fails, try the next available provider
smart.i18n.provider.fallback-enabled=true

# API call timeout in milliseconds (default: 10000)
smart.i18n.provider.timeout-ms=10000
```

**Auto-detection priority** (when `active` is not set):

| Priority | Provider | Condition |
|----------|----------|-----------|
| 1st | Google Cloud Translation | `google-cloud.api-key` is set |
| 2nd | Google Gemini | `gemini.api-key` is set |
| 3rd | OpenAI | `openai.api-key` is set |

### Cache Configuration

```properties
# Cache time-to-live in minutes (default: 60)
smart.i18n.cache.ttl-minutes=60

# Maximum cache entries (default: 10000)
smart.i18n.cache.max-size=10000
```

### Batch Translation

```properties
# Max texts per single API call (default: 50)
smart.i18n.batch.max-size=50
```

### Content Filtering

```properties
# Minimum string length to translate (default: 2)
smart.i18n.filter.min-length=2

# Additional regex patterns — matching strings are skipped
smart.i18n.filter.skip-patterns[0]=^SKU-.*$
smart.i18n.filter.skip-patterns[1]=^REF\\d+$
```

### Safety Guardrails

```properties
# Max translatable strings per request (default: 200)
smart.i18n.safeguard.max-strings-per-request=200

# Max character length per string (default: 5000)
smart.i18n.safeguard.max-text-length=5000

# Max recursive traversal depth (default: 32)
smart.i18n.safeguard.max-traversal-depth=32

# Max WebClient response buffer in MB (default: 2)
smart.i18n.safeguard.web-client-max-buffer-size-mb=2
```

---

## 🏗️ Architecture

```
in.devtamakuwala.smarti18nauto
├── annotation
│   ├── AutoTranslate.java              # Method/class annotation to enable translation
│   └── SkipTranslation.java            # Field annotation to exclude from translation
├── aop
│   └── TranslationAspect.java          # @Around aspect for service-layer methods
├── cache
│   └── TranslationCache.java           # Caffeine wrapper with composite cache keys
├── config
│   ├── SmartI18nAutoConfiguration.java  # @AutoConfiguration — all bean wiring
│   └── SmartI18nProperties.java         # @ConfigurationProperties with nested configs
├── engine
│   ├── TranslationEngine.java           # Core engine interface
│   └── DefaultTranslationEngine.java    # Orchestrates traverse → cache → batch → writeback
├── filter
│   └── ContentFilter.java              # Regex-based skip logic (IDs, numbers, URLs, etc.)
├── interceptor
│   ├── TranslationResponseBodyAdvice.java  # Intercepts outgoing responses
│   └── TranslationRequestBodyAdvice.java   # Intercepts incoming requests (opt-in)
├── provider
│   ├── TranslationProvider.java             # Strategy interface
│   ├── GoogleCloudTranslationProvider.java  # Google Cloud Translation v2
│   ├── GoogleGeminiTranslationProvider.java # Google Gemini generateContent
│   ├── OpenAiTranslationProvider.java       # OpenAI Chat Completions
│   └── TranslationProviderFactory.java      # Auto-detection + fallback chain
├── traversal
│   ├── ObjectTraverser.java            # Recursive object graph walker
│   └── StringReference.java            # Pointer for batch writeback
└── util
    ├── LanguageDetectionUtil.java      # Accept-Language / custom header / query param resolver
    └── TranslationMarker.java          # Request-scoped double-translation prevention
```

### Design Patterns

| Pattern | Usage |
|---------|-------|
| **Strategy** | `TranslationProvider` interface with 3 implementations |
| **Factory** | `TranslationProviderFactory` — auto-detection, explicit selection, fallback |
| **Template Method** | `DefaultTranslationEngine` — fixed pipeline with pluggable provider |
| **Visitor** | `ObjectTraverser` — walks object graph, collects `StringReference`s |
| **Decorator** | `ResponseBodyAdvice` wraps Spring MVC serialization |
| **Proxy** | AOP `@Around` aspect proxies service method returns |

### Translation Pipeline

```
Request arrives with Accept-Language: es
            │
            ▼
   ┌─────────────────────┐
   │  Language Detection  │  ← custom header → query param → Accept-Language → default
   └──────────┬──────────┘
              │  targetLang = "es"
              ▼
   ┌─────────────────────┐
   │  Object Traverser    │  ← recursive walk: String, List, Map, DTO fields
   │  (depth-limited)     │  ← skips: @SkipTranslation, static, transient
   └──────────┬──────────┘
              │  List<StringReference>
              ▼
   ┌─────────────────────┐
   │  Content Filter      │  ← skips: numbers, UUIDs, URLs, emails, enums, dates
   └──────────┬──────────┘
              │  filtered references
              ▼
   ┌─────────────────────┐
   │  Safeguard Limits    │  ← max strings/request, max text length
   └──────────┬──────────┘
              │  truncated + length-checked
              ▼
   ┌─────────────────────┐
   │  Deduplication       │  ← "Hello" appearing 5 times → sent once
   └──────────┬──────────┘
              │  unique texts
              ▼
   ┌─────────────────────┐
   │  Caffeine Cache      │  ← composite key: sourceLang:targetLang:fullText
   └──────────┬──────────┘
              │  cache misses only
              ▼
   ┌─────────────────────┐
   │  Provider Factory    │  ← primary → fallback chain
   │  (batch API call)    │  ← chunked by batch.max-size
   └──────────┬──────────┘
              │  translated texts
              ▼
   ┌─────────────────────┐
   │  Cache Store         │  ← store for next time
   │  + Writeback         │  ← fan out to all references (including deduped)
   └─────────────────────┘
              │
              ▼
   Translated response body
```

---

## 🏷️ Annotations

### `@AutoTranslate`

Apply to a **controller class** or **method** to enable automatic response translation.

```java
// Class-level: all endpoints in this controller are translated
@RestController
@RequestMapping("/api/products")
@AutoTranslate
public class ProductController {
    // ...
}

// Method-level: only this endpoint is translated
@RestController
public class MixedController {

    @GetMapping("/translated")
    @AutoTranslate
    public MessageDto getTranslated() {
        return new MessageDto("Hello World");
    }

    @GetMapping("/not-translated")
    public MessageDto getRaw() {
        return new MessageDto("Hello World"); // stays English
    }
}

// Override source/target locale
@AutoTranslate(sourceLocale = "fr", targetLocale = "de")
@GetMapping("/french-to-german")
public MessageDto frenchToGerman() {
    return new MessageDto("Bonjour le monde");
}
```

Also works on **service methods** via AOP:

```java
@Service
public class NotificationService {

    @AutoTranslate
    public NotificationDto getWelcomeMessage() {
        return new NotificationDto("Welcome to our platform!");
    }
}
```

### `@SkipTranslation`

Apply to **DTO fields** that should never be translated.

```java
public class ProductDto {

    @SkipTranslation
    private String sku;            // "SKU-12345" — never translated

    @SkipTranslation
    private String productCode;    // "ELECTRONICS_MOUSE" — never translated

    private String name;           // "Wireless Mouse" → translated
    private String description;    // "Great for gaming" → translated
    private double price;          // 29.99 — auto-skipped (number)
    private String id;             // "550e8400-..." — auto-skipped (UUID)
}
```

**Auto-skipped content** (no annotation needed):

| Type | Example | Reason |
|------|---------|--------|
| Numbers | `42`, `3.14`, `1.5e10` | Numeric pattern |
| UUIDs | `550e8400-e29b-41d4-...` | UUID pattern |
| URLs | `https://example.com` | URL pattern |
| Emails | `user@example.com` | Email pattern |
| Enums | `ORDER_STATUS`, `ACTIVE` | UPPER_SNAKE_CASE pattern |
| Dates | `2026-02-28T10:30:00` | ISO date pattern |
| Short tokens | `a`, `OK` | Below `min-length` |

---

## 💡 Example Usage

### DTO with Nested Objects

```java
public class OrderDto {
    private String orderId;                    // auto-skipped (short/ID-like)
    private String status;                     // "PENDING" — auto-skipped (enum)
    private CustomerDto customer;              // recursively traversed
    private List<OrderItemDto> items;          // list elements traversed
    private Map<String, String> metadata;      // map values translated
}

public class CustomerDto {
    private String name;                       // "John Doe" → translated
    @SkipTranslation
    private String email;                      // "john@example.com" — skipped
}

public class OrderItemDto {
    private String productName;                // "Wireless Mouse" → translated
    private String description;                // "Great for gaming" → translated
    private double price;                      // 29.99 — auto-skipped
}
```

### Controller

```java
@RestController
@RequestMapping("/api/orders")
@AutoTranslate
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/{id}")
    public OrderDto getOrder(@PathVariable String id) {
        return orderService.findById(id);
    }

    @GetMapping
    public List<OrderDto> getAllOrders() {
        return orderService.findAll();  // list of DTOs — all translated
    }
}
```

### Request Body Translation (Opt-In)

```properties
smart.i18n.translate-request-body=true
```

```java
@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    @PostMapping
    @AutoTranslate
    public FeedbackDto submitFeedback(@RequestBody FeedbackDto feedback) {
        // feedback.message is now in English (normalized from Accept-Language)
        return feedbackService.save(feedback);
    }
}
```

---

## 🧪 cURL Examples

### Basic Translation

```bash
# French
curl -s -H "Accept-Language: fr" http://localhost:8080/api/products/1 | jq

# Hindi
curl -s -H "Accept-Language: hi" http://localhost:8080/api/products/1 | jq

# Japanese
curl -s -H "Accept-Language: ja" http://localhost:8080/api/products/1 | jq

# Spanish (with quality values — primary language extracted)
curl -s -H "Accept-Language: es-ES,es;q=0.9,en;q=0.8" http://localhost:8080/api/products/1 | jq
```

### Custom Header

```properties
smart.i18n.header-name=X-Target-Language
```

```bash
curl -s -H "X-Target-Language: de" http://localhost:8080/api/products/1 | jq
```

### Query Parameter

```properties
smart.i18n.query-param=lang
```

```bash
curl -s "http://localhost:8080/api/products/1?lang=ko" | jq
```

### No Translation (Same Language)

```bash
# Source is English, target is English — no API call made
curl -s -H "Accept-Language: en" http://localhost:8080/api/products/1 | jq
```

### Test Caching (Second Call Is Instant)

```bash
# First call — hits API (slower)
time curl -s -H "Accept-Language: fr" http://localhost:8080/api/products/1 > /dev/null

# Second call — cache hit (instant)
time curl -s -H "Accept-Language: fr" http://localhost:8080/api/products/1 > /dev/null
```

---

## ⚡ Performance

### Caching Strategy

| Layer | Mechanism |
|-------|-----------|
| **L1: Caffeine Cache** | In-memory, O(1) lookup by composite key `sourceLang:targetLang:fullText` |
| **Deduplication** | Identical strings in a single DTO sent to API only once |
| **Batch API Calls** | All uncached strings per request sent in one API call |
| **Skip Filtering** | Numbers, UUIDs, URLs, etc. never reach the API |
| **Same-Language Short-Circuit** | `en → en` returns immediately, no traversal |

### Benchmarks (Approximate)

| Scenario | Latency |
|----------|---------|
| Cache hit (all strings cached) | **< 1ms** |
| Small DTO (3 fields, first call) | **200–500ms** (API call) |
| Large DTO (50 fields, first call) | **300–800ms** (single batch call) |
| Large DTO (50 fields, cached) | **< 2ms** |
| DTO with 50 duplicate strings | Same as 1 unique string (deduplication) |

### Tuning Tips

```properties
# Increase cache TTL for mostly-static content
smart.i18n.cache.ttl-minutes=1440  # 24 hours

# Increase cache size for high-cardinality content
smart.i18n.cache.max-size=100000

# Reduce batch size if provider has per-request limits
smart.i18n.batch.max-size=25

# Reduce timeout for faster failure
smart.i18n.provider.timeout-ms=5000
```

---

## 🔒 Security

### API Key Protection

| Threat | Mitigation |
|--------|------------|
| Key in URL query string | ❌ **Eliminated.** All providers use HTTP header auth (`X-Goog-Api-Key`, `Authorization: Bearer`) |
| Key in logs | ❌ **Masked.** All config `toString()` methods show `****` + last 4 chars |
| Key in Spring Actuator | ⚠️ Use `management.endpoint.configprops.show-values=NEVER` in production |
| Key in environment dumps | Use Spring Vault, AWS Secrets Manager, or `SPRING_APPLICATION_JSON` |

### Cost Protection

| Threat | Mitigation |
|--------|------------|
| Huge DTO → massive API bill | `max-strings-per-request=200` — excess strings silently skipped |
| Single giant text field | `max-text-length=5000` — long strings silently skipped |
| DDoS with varied languages | Caffeine cache absorbs repeated translations; consider rate limiting upstream |
| Deeply nested DTO → stack overflow | `max-traversal-depth=32` — traversal stops at depth limit |
| Oversized API response → OOM | `web-client-max-buffer-size-mb=2` — WebClient rejects large responses |

### Best Practices

```properties
# Production-recommended configuration
smart.i18n.provider.timeout-ms=5000
smart.i18n.safeguard.max-strings-per-request=100
smart.i18n.safeguard.max-text-length=2000
smart.i18n.cache.ttl-minutes=1440
smart.i18n.cache.max-size=50000
```

---

## 🧪 Testing Locally

### Prerequisites

- Java 21+
- Maven 3.9+
- At least one API key (Gemini is free-tier friendly)

### Step 1: Build and Install Locally

```bash
cd smart-i18n-auto
mvn clean install -Dgpg.skip=true
```

This installs `smart-i18n-auto-1.0.0.jar` into your local `~/.m2/repository`.

### Step 2: Create a Test Application

Create a new Spring Boot 4 project and add the dependency:

```xml
<dependency>
    <groupId>in.devtamakuwala</groupId>
    <artifactId>smart-i18n-auto</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Step 3: Configure a Provider

**`application.properties`:**

```properties
smart.i18n.gemini.api-key=YOUR_GEMINI_API_KEY
```

### Step 4: Create Test DTOs

```java
public class ProductDto {
    private String name;
    private String description;
    private double price;

    @SkipTranslation
    private String sku;

    private CategoryDto category;

    // constructors, getters, setters
}

public class CategoryDto {
    private String title;
    private String description;

    // constructors, getters, setters
}
```

### Step 5: Create a Test Controller

```java
@RestController
@RequestMapping("/api/test")
@AutoTranslate
public class TestController {

    @GetMapping("/simple")
    public Map<String, String> simple() {
        return Map.of(
            "greeting", "Hello World",
            "farewell", "Goodbye and thanks for all the fish"
        );
    }

    @GetMapping("/product")
    public ProductDto product() {
        CategoryDto category = new CategoryDto("Electronics", "Gadgets and devices");
        return new ProductDto("Wireless Mouse", "Great for gaming", 29.99, "SKU-001", category);
    }

    @GetMapping("/list")
    public List<ProductDto> productList() {
        return List.of(
            new ProductDto("Keyboard", "Mechanical RGB", 79.99, "SKU-002", null),
            new ProductDto("Monitor", "4K Ultra HD Display", 399.99, "SKU-003", null)
        );
    }
}
```

### Step 6: Run and Test

```bash
mvn spring-boot:run
```

#### Test Basic Translation

```bash
curl -s -H "Accept-Language: es" http://localhost:8080/api/test/simple | jq
```

Expected:
```json
{
  "greeting": "Hola Mundo",
  "farewell": "Adiós y gracias por todos los peces"
}
```

#### Test Nested DTO

```bash
curl -s -H "Accept-Language: ja" http://localhost:8080/api/test/product | jq
```

Expected:
```json
{
  "name": "ワイヤレスマウス",
  "description": "ゲームに最適",
  "price": 29.99,
  "sku": "SKU-001",
  "category": {
    "title": "エレクトロニクス",
    "description": "ガジェットとデバイス"
  }
}
```

Notice: `price` (number) and `sku` (`@SkipTranslation`) are untouched.

#### Test List of DTOs

```bash
curl -s -H "Accept-Language: fr" http://localhost:8080/api/test/list | jq
```

#### Test Same Language (No API Call)

```bash
curl -s -H "Accept-Language: en" http://localhost:8080/api/test/simple | jq
```

Response is returned instantly with original English text — no provider API call made.

#### Validate Caching

```bash
# First call — watch for "Translating X unique uncached strings" in logs
curl -s -H "Accept-Language: de" http://localhost:8080/api/test/simple | jq

# Second call — watch for "All X unique strings were cache hits" in logs
curl -s -H "Accept-Language: de" http://localhost:8080/api/test/simple | jq
```

Set logging to debug for full visibility:

```properties
logging.level.in.devtamakuwala.smarti18nauto=DEBUG
```

#### Test Request Body Translation

```properties
smart.i18n.translate-request-body=true
```

```bash
curl -s -X POST http://localhost:8080/api/test/feedback \
  -H "Content-Type: application/json" \
  -H "Accept-Language: fr" \
  -d '{"message": "Bonjour le monde"}' | jq
```

The incoming `message` is translated to English before your controller processes it.

#### Test Deduplication

Create a DTO with duplicate fields and check logs:

```java
@GetMapping("/duplicates")
@AutoTranslate
public Map<String, String> duplicates() {
    return Map.of(
        "field1", "Hello World",
        "field2", "Hello World",
        "field3", "Hello World",
        "field4", "Goodbye"
    );
}
```

```bash
curl -s -H "Accept-Language: fr" http://localhost:8080/api/test/duplicates | jq
```

Logs will show: `Translating 2 unique uncached strings (out of 2 unique, 4 total refs)` — only 2 unique texts sent to the API, not 4.

---

## ✅ Release Checklist

Before publishing a new version to Maven Central, verify:

- `pom.xml` has the correct `groupId`, `artifactId`, `version`, license, SCM, and developer metadata.
- `README.md` dependency snippets match `pom.xml` version.
- Unit tests pass and artifacts build locally.
- GPG signing works with the `release` profile.
- Sonatype Central credentials exist under server id `central` in `~/.m2/settings.xml`.

### Local verification commands

```bash
mvn clean verify
mvn clean deploy -Prelease -DskipTests
```

> Use `deploy` only when you are ready to publish. For day-to-day local testing, prefer `mvn clean install`.

---

## 🗺️ Roadmap

### v1.1.0

- [ ] Reactive (`WebFlux`) support via `ServerResponse` advice
- [ ] Rate limiter (Resilience4j / Bucket4j) for per-minute API budget
- [ ] Metrics export (Micrometer) — cache hit ratio, API latency, translation count
- [ ] Redis / Hazelcast distributed cache adapter

### v1.2.0

- [ ] Language detection from request body content (auto-detect source language)
- [ ] Glossary / terminology override map (domain-specific terms)
- [ ] Async/non-blocking translation via `Mono`/`Flux`
- [ ] DeepL provider
- [ ] Azure Cognitive Services Translator provider

### Future

- [ ] Admin endpoint — cache stats, provider health, live config reload
- [ ] Annotation `@TranslateField` for selective per-field provider override
- [ ] Spring Boot Actuator health indicator
- [ ] GraalVM native image compatibility
- [ ] Persistent cache (JDBC / MongoDB backed)
- [ ] Translation memory with quality scoring

---

## 🤝 Contributing

Contributions are welcome! Please:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

### Development Setup

```bash
git clone https://github.com/DevTamakuwala/smart-i18n-auto.git
cd smart-i18n-auto
mvn clean compile
mvn test
```

All tests should pass.

---

## 📄 License

This project is licensed under the **MIT License** — see the [LICENSE](LICENSE) file for details.

```
MIT License

Copyright (c) 2026 devtamakuwala

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

<p align="center">
  Built with ☕ and Spring Boot 4<br/>
  <strong>smart-i18n-auto</strong> — Because your API should speak every language.
</p>

