package com.abdelrahman.psp_simulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import com.abdelrahman.psp_simulator.dto.PaymentResponse;
import com.abdelrahman.psp_simulator.web.ApiHeaders;

import tools.jackson.databind.json.JsonMapper;

/**
 * Real HTTP requests against a running server, used for the races that MockMvc
 * cannot reproduce (separate connections, real transactions).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class HttpConcurrencyTests extends pspDatabaseSetup {

    private static final String AUTHORIZE_BODY = "{\"amount\":15000,\"currency\":\"EGP\"}";

    @LocalServerPort
    private int port;

    @Autowired
    private JsonMapper jsonMapper;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Test
    void captureAndVoidRaceOnlyOneWins() throws Exception {
        PaymentResponse created = authorize("K1");
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        Callable<Integer> capture = () -> {
            barrier.await();
            return send("POST", "/payments/" + created.id() + "/capture", "K2", null).status();
        };
        Callable<Integer> voidIt = () -> {
            barrier.await();
            return send("POST", "/payments/" + created.id() + "/void", "K3", null).status();
        };

        List<Future<Integer>> results = pool.invokeAll(List.of(capture, voidIt));
        pool.shutdown();

        List<Integer> statuses = results.stream().map(this::get).sorted().toList();
        assertEquals(List.of(200, 409), statuses);

        String finalState = jdbcTemplate.queryForObject(
                "SELECT state FROM payments_model WHERE id = ?", String.class, created.id());
        assertTrue(finalState.equals("CAPTURED") || finalState.equals("VOIDED"));
    }

    @Test
    void sameKeyAuthorizeRaceStoresOnePayment() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        Callable<PaymentResponse> call = () -> {
            barrier.await();
            return authorize("K1");
        };

        List<Future<PaymentResponse>> results = pool.invokeAll(List.of(call, call));
        pool.shutdown();

        PaymentResponse first = results.get(0).get();
        PaymentResponse second = results.get(1).get();

        assertEquals(first.id(), second.id());
        assertEquals(1, jdbcTemplate.queryForObject("SELECT count(*) FROM payments_model", Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT count(*) FROM payment_requests", Integer.class));
    }

    private PaymentResponse authorize(String key) throws Exception {
        HttpResult result = send("POST", "/payments", key, AUTHORIZE_BODY);
        assertEquals(200, result.status(), () -> "authorize failed: " + result.body());
        return jsonMapper.readValue(result.body(), PaymentResponse.class);
    }

    private HttpResult send(String method, String path, String key, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json");

        if (key != null) {
            builder.header(ApiHeaders.IDEMPOTENCY_KEY, key);
        }

        if ("POST".equals(method)) {
            builder.POST(body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body));
        } else {
            builder.GET();
        }

        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return new HttpResult(response.statusCode(), response.body());
    }

    private int get(Future<Integer> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private record HttpResult(int status, String body) {
    }
}
