# shadow-ig

Backend-only Spring Boot service that monitors configured Instagram Business/Creator public accounts and sends email alerts through Resend for newly detected content.

## Stack

- Java 25
- Spring Boot 4.1.x / Spring Framework 7
- Maven
- Spring Scheduling
- Spring Data JPA + persistent file-based H2
- Resend Java SDK
- Spring Boot Actuator
- JUnit + Mockito

## What is supported with official APIs

This project uses Instagram Graph API Business Discovery.

Supported:
- Public Instagram Business/Creator account media discovery (posts and reels when exposed through media feed)

Not supported by this implementation (official API limitation):
- Monitoring stories of unrelated third-party accounts via Business Discovery
- Arbitrary personal account scraping or private/unauthorized endpoints

When stories are enabled in YAML, the service logs that stories are unsupported for that account and continues processing supported content.

## Configuration

Copy `.env.example` to `.env` (or set env vars in your runtime environment):

- `INSTAGRAM_ACCESS_TOKEN`
- `INSTAGRAM_IG_USER_ID`
- `RESEND_API_KEY`

Main settings are in `src/main/resources/application.yml`:

- `instagram.monitoring.accounts` contains usernames and per-content toggles
- `instagram.monitoring.polling-interval` default is `60s`
- `notifications.email.recipient` and `notifications.email.sender`

No usernames or recipient addresses are hardcoded in code.

## Run

```bash
mvn spring-boot:run
```

## Monitoring behavior

- Polls enabled accounts on schedule
- Prevents overlapping runs
- Persists seen content IDs to prevent duplicate notifications
- First run records baseline without sending notifications unless `notify-on-startup=true`
- Sends one email per newly detected content item
- Retries temporary email failures with exponential backoff
- Continues other accounts if one fails

## Persistence

Persistent H2 database file:

- `./data/instagram-monitor.mv.db`

Stored data includes:
- username
- content ID/type/permalink
- publish timestamp
- detection timestamp
- notification status
- Resend message ID (when available)

## Test

```bash
mvn test
```
