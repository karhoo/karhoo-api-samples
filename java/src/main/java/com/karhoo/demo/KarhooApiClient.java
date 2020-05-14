package com.karhoo.demo;

import com.google.gson.Gson;
import com.karhoo.demo.model.AuthRequest;
import com.karhoo.demo.model.AuthResponse;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

class KarhooApiClient {
    private String username;
    private String password;
    private String jwt;
    private String refreshToken;

    public static String HOST = "rest.sandbox.karhoo.com"; //rest.karhoo.com for Production
    public static String SCHEME = "https";
    public static String REFRESH_TOKEN_URL = "/v1/auth/refresh";
    public static String AUTHENTICATE_URL = "/v1/auth/token";

    // Initialise the Karhoo API client.
    // In case you use Serverless on your backend it is important to cache your JWT (for example using Redis or MemCache)
    // because too many authentication attempts will generate rate limits and even lock out your account for security reasons.
    // In case a JWT and refresh token are provided in the constructor they will be used automatically.
    public KarhooApiClient(String username, String password, String jwt, String refreshToken) {
        this.username = username;
        this.password = password;
        this.jwt = jwt;
        this.refreshToken = refreshToken;
    }

    // Should be called if no JWT is present or if using the refresh token fails.
    // it overrides the jwt and refresh token
    public void authenticate() throws Exception {
        Gson gson = new Gson();
        var requestBody = new AuthRequest();
        requestBody.username = this.username;
        requestBody.password = this.password;

        HttpRequest request = createDefaultBuilder(AUTHENTICATE_URL)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson((requestBody))))
                .build();

        HttpResponse response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        // handling the expected status codes
        handleAuthResponse(gson, response);
    }

    private void refreshJwt() throws Exception {
        Gson gson = new Gson();

        Map<String, String> map = new HashMap<>();
        map.put("refresh_token", refreshToken);

        HttpRequest request = createDefaultBuilder(REFRESH_TOKEN_URL)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(map)))
                .build();

        HttpResponse response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        handleAuthResponse(gson, response);
    }

    private void handleAuthResponse(Gson gson, HttpResponse<String> response) throws Exception {
        System.out.println("status code: "+response.statusCode());
        switch (response.statusCode()) {
            case 401:
                throw new Exception(String.format("The username and password are incorrect or it was disabled for security reasons. Please contact Karhoo. %s", response.body()));
            case 403:
                throw new Exception(String.format("There is an issue with your account permission. Please contact Karhoo. %s", response.body()));
            case 429:
                throw new Exception(String.format("Too many authentication requests: %s", response.body()));
            case 201:
            case 200:
                AuthResponse authResponse = gson.fromJson(response.body(), AuthResponse.class);
                this.jwt = authResponse.access_token;
                this.refreshToken = authResponse.refresh_token;
                System.out.println(String.format("JWT: %s is valid for %d seconds. After this you can use refresh token %s to get a new JWT. " +
                        "The Karhoo client will refresh it automatically", authResponse.access_token, authResponse.expires_in, authResponse.refresh_token));

                break;
            default:
                throw new Exception(String.format("Unexpected status returned: %d, %s", response.statusCode(), response.body()));
        }
    }

    // make the API call and handles errors such as refreshing the JWT automatically.
    private String callApiSecurelyAndHandleErrors(HttpRequest.Builder builder) throws Exception {
        if (this.jwt.equals("")) {
            authenticate();
        }

        HttpRequest request = builder
                .header("Authorization", String.format("Bearer %s", this.jwt))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        return handleErrors(request, response);
    }

    private String handleErrors(HttpRequest request, HttpResponse<String> response) throws Exception {
        switch (response.statusCode()) {
            case 401:
                refreshJwt();
            case 403:
                throw new Exception(String.format("There is an issue with your account permission. Please contact Karhoo. %s", response.body()));
            case 429:
                throw new Exception(String.format("Too many authentication requests: %s", response.body()));
            case 201:
            case 200:
                return response.body();
            default:
                throw new Exception(String.format("Unexpected status returned: %s %d, %s", request.uri().toString(), response.statusCode(), response.body()));
        }
    }

    <RequestBody, ResponseBody> ResponseBody POST(String path, RequestBody requestBody, java.lang.Class<ResponseBody> classofResponseBody) throws Exception {
        Gson gson = new Gson();
        HttpRequest.Builder reqBuilder = createDefaultBuilder(path)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)));

        String json = callApiSecurelyAndHandleErrors(reqBuilder);

        return gson.fromJson(json, classofResponseBody);
    }

    <ResponseBody> ResponseBody GET(String path, java.lang.Class<ResponseBody> classofResponseBody) throws Exception {
        HttpRequest.Builder reqBuilder = createDefaultBuilder(path)
                .GET();
        String json = callApiSecurelyAndHandleErrors(reqBuilder);

        return new Gson().fromJson(json, classofResponseBody);
    }

    private HttpRequest.Builder createDefaultBuilder(String path) throws URISyntaxException {
        URI uri = new URI(SCHEME, HOST, path, "");
        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(uri)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(5));
        return reqBuilder;
    }
}
