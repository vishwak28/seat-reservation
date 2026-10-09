import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Seat reservation burst test.
 *
 * Usage:  java scripts/Burst.java <BASE_URL> [users]
 * Env:    ADMIN_SECRET (default: dev-admin-secret)
 */
public class Burst {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static String baseUrl;
    private static String adminSecret;
    private static int failures = 0;

    /** A response reduced to what we need. */
    static class Resp {
        final int status;
        final String body;
        final boolean replayed;

        Resp(int status, String body, boolean replayed) {
            this.status = status;
            this.body = body;
            this.replayed = replayed;
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: java scripts/Burst.java <BASE_URL> [users]");
            return;
        }
        baseUrl = args[0].replaceAll("/+$", "");
        int users = args.length > 1 ? Integer.parseInt(args[1]) : 200;
        adminSecret = System.getenv().getOrDefault("ADMIN_SECRET", "dev-admin-secret");
        String run = Long.toString(System.currentTimeMillis(), 36);

        System.out.println("Target: " + baseUrl + "   users: " + users + "   run id: " + run);

        Resp ready = send("GET", "/actuator/health/readiness", null, null, null);
        System.out.println("Readiness: " + ready.status + " " + ready.body);
        if (ready.status != 200) {
            System.out.println("Service is not ready, aborting.");
            System.exit(2);
        }

        hotSeatStorm(run, users);

        System.out.println(failures == 0
                ? "\nALL CHECKS PASSED"
                : "\n" + failures + " CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------
    // Scenario 1: many users, one hot seat
    // ------------------------------------------------------------------

    private static void hotSeatStorm(String run, int users) throws Exception {
        System.out.println("\n=== Hot-seat storm: " + users + " users, one seat ===");

        String admin = adminToken();
        List<String> seats = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            seats.add("A" + i);
        }
        String showId = createShow(admin, "hot-" + run, seats, null);
        List<String> tokens = mintTokens("hot-" + run, users);

        List<Callable<Resp>> tasks = new ArrayList<>();
        for (int i = 0; i < users; i++) {
            final String token = tokens.get(i);
            final String key = "hot-" + run + "-" + i;
            tasks.add(() -> reserve(showId, token, key, List.of("A1")));
        }

        long started = System.nanoTime();
        List<Resp> results = fire(tasks, Math.min(users, 500));
        long millis = (System.nanoTime() - started) / 1_000_000;

        Map<String, Integer> outcomes = tally(results);
        printOutcomes(outcomes, results.size(), millis);

        int winners = outcomes.getOrDefault("201 confirmed", 0);
        int taken = outcomes.getOrDefault("409 seat_taken", 0);
        check("exactly one 201 for the hot seat", winners == 1, "got " + winners);
        check("everyone else got a clean 409 seat_taken", taken == users - 1,
                "got " + taken + ", expected " + (users - 1));
        check("zero 5xx and zero network errors", badCount(outcomes) == 0,
                "got " + badCount(outcomes));

        int[] state = showState(showId);
        check("show state readable", state != null, "");
        if (state != null) {
            reconcile(state, 1);
        }
    }

    // ------------------------------------------------------------------
    // Reconciliation and reporting
    // ------------------------------------------------------------------

    /** state = {total, available, held, confirmed} */
    private static void reconcile(int[] state, int expectedConfirmed) {
        System.out.println("  Final state: total=" + state[0] + " available=" + state[1]
                + " held=" + state[2] + " confirmed=" + state[3]);
        check("available + held + confirmed == total_seats",
                state[1] + state[2] + state[3] == state[0],
                (state[1] + state[2] + state[3]) + " vs " + state[0]);
        check("confirmed seats == " + expectedConfirmed, state[3] == expectedConfirmed,
                "got " + state[3]);
    }

    private static String classify(Resp r) {
        if (r.status < 0) {
            return "network_error";
        }
        if (r.status == 201) {
            return r.replayed ? "201 replay" : "201 confirmed";
        }
        if (r.status == 409) {
            String reason = field(r.body, "reason");
            return "409 " + (reason == null ? "unknown" : reason);
        }
        if (r.status >= 500) {
            return "5xx (" + r.status + ")";
        }
        return r.status + " other";
    }

    private static Map<String, Integer> tally(List<Resp> results) {
        Map<String, Integer> counts = new TreeMap<>();
        for (Resp r : results) {
            counts.merge(classify(r), 1, Integer::sum);
        }
        return counts;
    }

    private static int badCount(Map<String, Integer> outcomes) {
        int bad = 0;
        for (Map.Entry<String, Integer> e : outcomes.entrySet()) {
            if (e.getKey().startsWith("5xx") || e.getKey().equals("network_error")) {
                bad += e.getValue();
            }
        }
        return bad;
    }

    private static void printOutcomes(Map<String, Integer> outcomes, int total, long millis) {
        double perSecond = millis == 0 ? total : total * 1000.0 / millis;
        System.out.printf("Outcome distribution (%d requests in %d ms, %.0f req/s):%n",
                total, millis, perSecond);
        for (Map.Entry<String, Integer> e : outcomes.entrySet()) {
            System.out.printf("  %-24s %6d%n", e.getKey(), e.getValue());
        }
    }

    private static void check(String name, boolean ok, String detail) {
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + name
                + (detail.isEmpty() ? "" : "   (" + detail + ")"));
        if (!ok) {
            failures++;
        }
    }

    // ------------------------------------------------------------------
    // API helpers
    // ------------------------------------------------------------------

    private static String adminToken() {
        Resp r = send("POST", "/auth/token", null, null,
                "{\"user_id\":\"burst-admin\",\"admin_secret\":\"" + adminSecret + "\"}");
        String token = field(r.body, "token");
        if (r.status != 200 || token == null) {
            throw new IllegalStateException("Could not get an admin token: " + r.status
                    + " " + r.body + " (is ADMIN_SECRET correct?)");
        }
        return token;
    }

    private static String createShow(String adminToken, String name, List<String> seats,
                                     Integer perUserLimit) {
        String limit = perUserLimit == null ? "" : ",\"per_user_limit\":" + perUserLimit;
        Resp r = send("POST", "/shows", adminToken, null,
                "{\"name\":\"" + name + "\",\"seats\":" + seatsJson(seats)
                        + ",\"price_paise\":25000" + limit + "}");
        String id = field(r.body, "id");
        if (r.status != 201 || id == null) {
            throw new IllegalStateException("Could not create show: " + r.status + " " + r.body);
        }
        return id;
    }

    private static List<String> mintTokens(String prefix, int users) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(users, 50));
        List<Future<String>> futures = new ArrayList<>();
        for (int i = 0; i < users; i++) {
            final String userId = prefix + "-" + i;
            futures.add(pool.submit(() -> {
                Resp r = send("POST", "/auth/token", null, null,
                        "{\"user_id\":\"" + userId + "\"}");
                String token = field(r.body, "token");
                if (r.status != 200 || token == null) {
                    throw new IllegalStateException("Token request failed: " + r.status + " " + r.body);
                }
                return token;
            }));
        }
        List<String> tokens = new ArrayList<>();
        for (Future<String> f : futures) {
            tokens.add(f.get());
        }
        pool.shutdown();
        return tokens;
    }

    private static Resp reserve(String showId, String token, String key, List<String> seats) {
        return send("POST", "/shows/" + showId + "/reserve", token, key,
                "{\"seats\":" + seatsJson(seats) + "}");
    }

    /** Returns {total, available, held, confirmed}, or null if unreadable. */
    private static int[] showState(String showId) {
        Resp r = send("GET", "/shows/" + showId, null, null, null);
        if (r.status != 200) {
            return null;
        }
        return new int[]{
                intField(r.body, "total_seats"),
                intField(r.body, "available"),
                intField(r.body, "held"),
                intField(r.body, "confirmed")};
    }

    // ------------------------------------------------------------------
    // Concurrency and HTTP plumbing
    // ------------------------------------------------------------------

    /**
     * Runs all tasks on a pool of the given size. The first batch of threads waits
     * at a starting line and is released together, so the requests are simultaneous.
     */
    private static List<Resp> fire(List<Callable<Resp>> tasks, int threads) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(Math.min(threads, tasks.size()));
        CountDownLatch start = new CountDownLatch(1);

        List<Future<Resp>> futures = new ArrayList<>();
        for (Callable<Resp> task : tasks) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                return task.call();
            }));
        }

        ready.await();
        start.countDown();

        List<Resp> results = new ArrayList<>();
        for (Future<Resp> f : futures) {
            results.add(f.get());
        }
        pool.shutdown();
        return results;
    }

    private static Resp send(String method, String path, String token,
                             String idempotencyKey, String json) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json");
            if (token != null) {
                builder.header("Authorization", "Bearer " + token);
            }
            if (idempotencyKey != null) {
                builder.header("Idempotency-Key", idempotencyKey);
            }
            if (json != null) {
                builder.method(method, HttpRequest.BodyPublishers.ofString(json));
            } else {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            }
            HttpResponse<String> response = CLIENT.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString());
            boolean replayed = response.headers().firstValue("Idempotent-Replayed").isPresent();
            return new Resp(response.statusCode(), response.body(), replayed);
        } catch (Exception e) {
            return new Resp(-1, String.valueOf(e), false);
        }
    }

    private static String seatsJson(List<String> seats) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < seats.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append("\"").append(seats.get(i)).append("\"");
        }
        return sb.append("]").toString();
    }

    private static String field(String json, String name) {
        Matcher m = Pattern.compile("\"" + name + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private static int intField(String json, String name) {
        Matcher m = Pattern.compile("\"" + name + "\"\\s*:\\s*(\\d+)").matcher(json);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }
}