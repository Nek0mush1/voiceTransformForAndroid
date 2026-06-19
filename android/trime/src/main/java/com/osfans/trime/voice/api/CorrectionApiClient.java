package com.osfans.trime.voice.api;

import com.osfans.trime.voice.model.TextCorrectionRequest;
import com.osfans.trime.voice.model.TextCorrectionResponse;
import com.osfans.trime.voice.model.LlmCallLogResponse;
import com.osfans.trime.voice.model.LlmConfigResponse;
import com.osfans.trime.voice.model.LlmConfigTestResponse;
import com.osfans.trime.voice.model.ProfileResponse;
import com.osfans.trime.voice.model.TermCreateRequest;
import com.osfans.trime.voice.model.TermResponse;

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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CorrectionApiClient {
    private static final ExecutorService EXECUTOR_SERVICE = Executors.newCachedThreadPool();
    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int JSON_READ_TIMEOUT_MS = 15000;
    private static final int TEXT_CORRECTION_READ_TIMEOUT_MS = 90000;
    private static final int AUDIO_CORRECTION_READ_TIMEOUT_MS = 120000;

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
                connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
                connection.setReadTimeout(TEXT_CORRECTION_READ_TIMEOUT_MS);
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
                connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
                connection.setReadTimeout(AUDIO_CORRECTION_READ_TIMEOUT_MS);
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

    public void getProfile(String userId, ProfileCallback callback) {
        EXECUTOR_SERVICE.execute(() -> {
            try {
                String responseText = requestJson("GET", "/api/v1/profile/" + encode(userId), null);
                JSONObject object = new JSONObject(responseText);
                callback.onSuccess(new ProfileResponse(
                        object.getString("user_id"),
                        object.getString("profile_text"),
                        object.getString("updated_at")
                ));
            } catch (Exception exception) {
                callback.onError(exception);
            }
        });
    }

    public void updateProfile(String userId, String profileText, ProfileCallback callback) {
        EXECUTOR_SERVICE.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("profile_text", profileText);
                String responseText = requestJson("PUT", "/api/v1/profile/" + encode(userId), body);
                JSONObject object = new JSONObject(responseText);
                callback.onSuccess(new ProfileResponse(
                        object.getString("user_id"),
                        object.getString("profile_text"),
                        object.getString("updated_at")
                ));
            } catch (Exception exception) {
                callback.onError(exception);
            }
        });
    }

    public void listTerms(String userId, TermsCallback callback) {
        EXECUTOR_SERVICE.execute(() -> {
            try {
                String responseText = requestJson("GET", "/api/v1/terms?user_id=" + encode(userId), null);
                callback.onSuccess(parseTerms(responseText));
            } catch (Exception exception) {
                callback.onError(exception);
            }
        });
    }

    public void createTerm(TermCreateRequest request, TermCallback callback) {
        EXECUTOR_SERVICE.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("user_id", request.userId);
                body.put("term", request.term);
                body.put("category", request.category);
                body.put("aliases", new JSONArray(request.aliases));
                body.put("weight", request.weight);
                String responseText = requestJson("POST", "/api/v1/terms", body);
                callback.onSuccess(parseTerm(new JSONObject(responseText)));
            } catch (Exception exception) {
                callback.onError(exception);
            }
        });
    }

    public void deleteTerm(int termId, EmptyCallback callback) {
        EXECUTOR_SERVICE.execute(() -> {
            try {
                requestJson("DELETE", "/api/v1/terms/" + termId, null);
                callback.onSuccess();
            } catch (Exception exception) {
                callback.onError(exception);
            }
        });
    }

    public void getLlmConfig(LlmConfigCallback callback) {
        EXECUTOR_SERVICE.execute(() -> {
            try {
                String responseText = requestJson("GET", "/api/v1/llm-config", null);
                callback.onSuccess(parseLlmConfig(new JSONObject(responseText)));
            } catch (Exception exception) {
                callback.onError(exception);
            }
        });
    }

    public void updateLlmConfig(String llmBaseUrl, String apiKey, String model, String wireApi, LlmConfigCallback callback) {
        EXECUTOR_SERVICE.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("base_url", llmBaseUrl);
                body.put("api_key", apiKey);
                body.put("model", model);
                body.put("wire_api", wireApi);
                String responseText = requestJson("PUT", "/api/v1/llm-config", body);
                callback.onSuccess(parseLlmConfig(new JSONObject(responseText)));
            } catch (Exception exception) {
                callback.onError(exception);
            }
        });
    }

    public void testLlmConfig(LlmConfigTestCallback callback) {
        EXECUTOR_SERVICE.execute(() -> {
            try {
                String responseText = requestJson("POST", "/api/v1/llm-config/test", new JSONObject());
                JSONObject object = new JSONObject(responseText);
                callback.onSuccess(new LlmConfigTestResponse(
                        object.getBoolean("success"),
                        object.optString("message", ""),
                        object.optString("sample_output", "")
                ));
            } catch (Exception exception) {
                callback.onError(exception);
            }
        });
    }

    public void diagnoseBackend(DiagnosticCallback callback) {
        EXECUTOR_SERVICE.execute(() -> {
            try {
                String healthText = requestJson("GET", "/health", null);
                String statusText = requestJson("GET", "/api/v1/debug/status", null);
                callback.onSuccess(healthText, statusText);
            } catch (Exception exception) {
                callback.onError(exception);
            }
        });
    }

    public void listLlmCallLogs(LlmCallLogsCallback callback) {
        EXECUTOR_SERVICE.execute(() -> {
            try {
                String responseText = requestJson("GET", "/api/v1/debug/llm-calls?limit=50", null);
                callback.onSuccess(parseLlmCallLogs(responseText));
            } catch (Exception exception) {
                callback.onError(exception);
            }
        });
    }

    private String requestJson(String method, String path, JSONObject body) throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(baseUrl + path);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(JSON_READ_TIMEOUT_MS);
            connection.setRequestProperty("Accept", "application/json");

            if (body != null) {
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setDoOutput(true);
                try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream(), StandardCharsets.UTF_8))) {
                    writer.write(body.toString());
                }
            }

            int statusCode = connection.getResponseCode();
            InputStream stream = statusCode >= 200 && statusCode < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String responseText = readAll(stream);
            if (statusCode < 200 || statusCode >= 300) {
                throw new IllegalStateException("HTTP " + statusCode + ": " + responseText);
            }
            return responseText;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
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
                object.getString("reason"),
                object.optString("correction_method", "unknown"),
                object.optBoolean("llm_used", false),
                object.optString("llm_error", ""),
                object.optString("trace_id", "")
        );
    }

    private List<LlmCallLogResponse> parseLlmCallLogs(String responseText) throws Exception {
        JSONArray array = new JSONArray(responseText);
        List<LlmCallLogResponse> logs = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.getJSONObject(i);
            logs.add(new LlmCallLogResponse(
                    object.optInt("id", 0),
                    object.optString("trace_id", ""),
                    object.optString("user_id", ""),
                    object.optString("raw_text", ""),
                    object.optString("fallback_text", ""),
                    object.optString("output_text", ""),
                    object.optBoolean("success", false),
                    object.optString("error", ""),
                    object.optString("correction_method", "unknown"),
                    object.optString("base_url", ""),
                    object.optString("model", ""),
                    object.optString("wire_api", ""),
                    object.optInt("duration_ms", 0),
                    object.optString("created_at", "")
            ));
        }
        return logs;
    }

    private List<TermResponse> parseTerms(String responseText) throws Exception {
        JSONArray array = new JSONArray(responseText);
        List<TermResponse> terms = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            terms.add(parseTerm(array.getJSONObject(i)));
        }
        return terms;
    }

    private TermResponse parseTerm(JSONObject object) throws Exception {
        JSONArray aliasesJson = object.getJSONArray("aliases");
        List<String> aliases = new ArrayList<>();
        for (int i = 0; i < aliasesJson.length(); i++) {
            aliases.add(aliasesJson.getString(i));
        }
        return new TermResponse(
                object.getInt("id"),
                object.getString("user_id"),
                object.getString("term"),
                object.optString("category", ""),
                aliases,
                object.optDouble("weight", 1.0),
                object.optString("created_at", "")
        );
    }

    private LlmConfigResponse parseLlmConfig(JSONObject object) {
        return new LlmConfigResponse(
                object.optString("base_url", ""),
                object.optString("model", ""),
                object.optString("wire_api", "chat_completions"),
                object.optBoolean("configured", false),
                object.optString("api_key_masked", ""),
                object.optString("updated_at", "")
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

    private String encode(String value) throws Exception {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
    }

    public interface Callback {
        void onSuccess(TextCorrectionResponse response);

        void onError(Exception exception);
    }

    public interface ProfileCallback {
        void onSuccess(ProfileResponse response);

        void onError(Exception exception);
    }

    public interface TermsCallback {
        void onSuccess(List<TermResponse> response);

        void onError(Exception exception);
    }

    public interface TermCallback {
        void onSuccess(TermResponse response);

        void onError(Exception exception);
    }

    public interface EmptyCallback {
        void onSuccess();

        void onError(Exception exception);
    }

    public interface LlmConfigCallback {
        void onSuccess(LlmConfigResponse response);

        void onError(Exception exception);
    }

    public interface LlmConfigTestCallback {
        void onSuccess(LlmConfigTestResponse response);

        void onError(Exception exception);
    }

    public interface DiagnosticCallback {
        void onSuccess(String healthText, String statusText);

        void onError(Exception exception);
    }

    public interface LlmCallLogsCallback {
        void onSuccess(List<LlmCallLogResponse> response);

        void onError(Exception exception);
    }
}
