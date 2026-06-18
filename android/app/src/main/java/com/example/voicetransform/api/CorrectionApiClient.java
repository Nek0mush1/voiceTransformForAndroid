package com.example.voicetransform.api;

import com.example.voicetransform.model.TextCorrectionRequest;
import com.example.voicetransform.model.TextCorrectionResponse;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CorrectionApiClient {
    private final String baseUrl;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public CorrectionApiClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public void correctText(TextCorrectionRequest request, Callback callback) {
        executorService.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(baseUrl + "/api/v1/correct-text");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(10000);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setRequestProperty("Accept", "application/json");
                connection.setDoOutput(true);

                JSONObject body = new JSONObject();
                body.put("user_id", request.userId);
                body.put("raw_text", request.rawText);
                body.put("app_context", request.appContext);

                try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream(), StandardCharsets.UTF_8))) {
                    writer.write(body.toString());
                }

                int statusCode = connection.getResponseCode();
                InputStream stream = statusCode >= 200 && statusCode < 300
                        ? connection.getInputStream()
                        : connection.getErrorStream();
                String responseText = readAll(stream);
                if (statusCode < 200 || statusCode >= 300) {
                    throw new IllegalStateException("HTTP " + statusCode + ": " + responseText);
                }

                callback.onSuccess(parseResponse(responseText));
            } catch (Exception exception) {
                callback.onError(exception);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private TextCorrectionResponse parseResponse(String responseText) throws Exception {
        JSONObject object = new JSONObject(responseText);
        JSONArray matchedTermsJson = object.getJSONArray("matched_terms");
        List<String> matchedTerms = new ArrayList<>();
        for (int i = 0; i < matchedTermsJson.length(); i++) {
            matchedTerms.add(matchedTermsJson.getString(i));
        }

        return new TextCorrectionResponse(
                object.getString("raw_text"),
                object.getString("corrected_text"),
                matchedTerms,
                object.getString("reason")
        );
    }

    private String readAll(InputStream stream) throws Exception {
        if (stream == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }

    public interface Callback {
        void onSuccess(TextCorrectionResponse response);

        void onError(Exception exception);
    }
}
