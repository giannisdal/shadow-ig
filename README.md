# shadow-ig

Spring Boot backend that checks public Instagram accounts through the StoriesIG REST API
and emails a daily digest through Resend when it finds new content.

## Daily monitoring

The default account is `example_account`. Stories and posts are checked once a day at
**21:00 Europe/Athens**, including daylight saving changes. The application must remain
running, and the host must be awake with network access at that time. Restarting it does
not trigger an immediate check or catch up a missed scheduled run.

Each daily check makes one `/stories` call and one `/posts` call per account. There are no
automatic API retries or pagination calls. That is 60–62 requests per month per account,
within the provider's advertised free allowance of 1,000 requests/month. Manual checks,
additional accounts and enabling the separate reels endpoint consume additional requests.
Requests are spaced below the advertised two requests/second limit. Authentication,
quota and rate-limit errors stop the cycle; unknown API responses are logged as failures,
never interpreted as an empty feed.

Only the first response page is fetched and up to `max-items-per-account` items of each
requested type are processed. Stories that appear and expire between daily checks can be
missed. The API is an external provider, so its availability and returned content determine
what the monitor can detect.

One email per account contains all newly detected items and Instagram links. Previously
seen IDs are persisted in H2 to avoid duplicate notifications. On every check, posts and reels are emailed only if published after yesterday at 21:00
and through today at 21:00 in Europe/Athens (including daylight saving changes). Older posts and posts without a publication
timestamp are recorded silently. Active stories keep their existing notification behavior. Set `notify-on-startup=false`
to establish the entire initial baseline silently. Failed email sends are not marked as
processed, so they can be retried on the next check.

## Configuration

Java 25 and Maven are required. Copy `.env.example` to `.env` and configure:

```properties
INSTAGRAM_USERNAME=example_account
STORIESIG_API_KEY=your-free-api-key
RESEND_API_KEY=your-resend-key
NOTIFICATION_EMAIL_RECIPIENT=you@example.com
NOTIFICATION_EMAIL_SENDER=onboarding@resend.dev
INSTAGRAM_MONITOR_CRON=0 0 21 * * *
```

Get a free StoriesIG API key from [scraperapi.to](https://scraperapi.to).
[API documentation](https://storiesig.info/en/api/) describes the `/stories` and `/posts`
endpoints. The key is sent only to that API as a Bearer token. Instagram credentials are
not needed. `.env` is ignored by Git; existing environment variables take precedence.

More accounts, content toggles, request timeout, cron and timezone are configured in
`src/main/resources/application.yml`. Reels returned in the posts response are included;
the separate reels endpoint is disabled by default to save requests.

## Run

```bash
mvn spring-boot:run
```

To check immediately, send a real digest if new content exists, and exit:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--instagram.monitoring.run-once=true --instagram.monitoring.cron=- --spring.main.web-application-type=none"
```

For a foreground packaged process:

```bash
mvn package
java -jar target/shadow-ig-0.0.1-SNAPSHOT.jar
```

The default HTTP port is `8081` (override with `SERVER_PORT`). On the server, place the
JAR and configured `.env` in the same working directory, with Java 25 installed, then run:

```bash
nohup java -jar shadow-ig-0.0.1-SNAPSHOT.jar > monitor.log 2>&1 < /dev/null &
```

The `data/` directory is created relative to that working directory; preserve it between
deployments to retain duplicate detection. The application schedules checks at 21:00
Europe/Athens regardless of the server's system timezone.

The previous public Instagram browser implementation remains available by explicitly
setting `instagram.monitoring.provider=browser`; StoriesIG is the default provider.

## Persistence and tests

Seen IDs, publication/detection times and Resend message IDs are stored in
`./data/instagram-monitor.mv.db`. Existing records are preserved.

```bash
mvn test
```

The test suite uses mocked API/email clients and never sends live email. The manual
`run-once` command above uses real credentials and does send email.
