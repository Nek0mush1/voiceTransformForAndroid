package com.example.voicetransform.api;

import com.example.voicetransform.model.TextCorrectionRequest;
import com.example.voicetransform.model.TextCorrectionResponse;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
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
    private static final ExecutorService EXECUTOR_SERVICE = Executors.newCachedThreadPool();

    private final String baseUrl;

    public CorrectionApiClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public void correctText(TextCorrectionRequest request, Callback callback) {
        EXECUTOR_SERVICE.execute(() -> {
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

    public void correctAudio(File audioFile, String userId, String appContext, Callback callback) {
        EXECUTOR_SERVICE.execute(() -> {
            HttpURLConnection connection = null;
            String boundary = "VoiceTransformBoundary" + System.currentTimeMillis();
            try {
                URL url = new URL(baseUrl + "/api/v1/correct-audio");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(60000);
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                connection.setRequestProperty("Accept", "application/json");
                connection.setDoOutput(true);

                try (DataOutputStream output = new DataOutputStream(connection.getOutputStream())) {
                    writeFormField(output, boundary, "user_id", userId);
                    writeFormField(output, boundary, "app_context", appContext);
                    writeFileField(output, boundary, "audio", audioFile, "audio/mp4");
                    output.writeBytes("--" + boundary + "--\r\n");
                    output.flush();
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

    private void writeFormField(DataOutputStream output, String boundary, String name, String value) throws Exception {
        output.writeBytes("--" + boundary + "\r\n");
        output.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"\r\n");
        output.writeBytes("Content-Type: text/plain; charset=UTF-8\r\n\r\n");
        output.write(value.getBytes(StandardCharsets.UTF_8));
        output.writeBytes("\r\n");
    }

    private void writeFileField(DataOutputStream output, String boundary, String name, File file, String contentType) throws Exception {
        output.writeBytes("--" + boundary + "\r\n");
        output.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + file.getName() + "\"\r\n");
        output.writeBytes("Content-Type: " + contentType + "\r\n\r\n");
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
        }
        output.writeBytes("\r\n");
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
