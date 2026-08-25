package com.mcai.chat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

public final class ImageUploader {
	private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";

	private ImageUploader() {
	}

	public static String upload(String imageUrl, ChatConfig config, HttpClient client) {
		String type = config.imageUploaderType;
		if (type == null) {
			type = "none";
		}
		switch (type) {
			case "catbox":
				return uploadToCatbox(imageUrl, config, client);
			case "0x0":
				return uploadTo0x0(imageUrl, config, client);
			case "imgbb":
				return uploadToImgbb(imageUrl, config, client);
			case "custom":
				return uploadToCustomHost(imageUrl, config, client);
			default:
				return imageUrl;
		}
	}

	private static byte[] download(String imageUrl, ChatConfig config, HttpClient client) {
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(imageUrl))
				.timeout(Duration.ofSeconds(config.imageTimeoutSeconds))
				.version(HttpClient.Version.HTTP_1_1)
				.header("User-Agent", USER_AGENT)
				.GET()
				.build();
		HttpResponse<byte[]> response;
		try {
			response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
		} catch (Exception e) {
			System.err.println("[mcai] Image download failed: " + e);
			return null;
		}
		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			System.err.println("[mcai] Image download failed: HTTP " + response.statusCode());
			return null;
		}
		return response.body();
	}

	private static String multipart(String fieldName, String boundary, byte[] imageData) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		write(out, "--" + boundary + "\r\n");
		write(out, "Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"image.png\"\r\n");
		write(out, "Content-Type: image/png\r\n\r\n");
		out.write(imageData, 0, imageData.length);
		write(out, "\r\n--" + boundary + "--\r\n");
		return new String(out.toByteArray(), StandardCharsets.ISO_8859_1);
	}

	private static void write(ByteArrayOutputStream out, String s) {
		for (int i = 0; i < s.length(); i++) {
			out.write(s.charAt(i) & 0xFF);
		}
	}

	private static String uploadToCatbox(String imageUrl, ChatConfig config, HttpClient client) {
		byte[] data = download(imageUrl, config, client);
		if (data == null) {
			return null;
		}
		String boundary = "----AiChatModCatbox" + UUID.randomUUID();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		write(out, "--" + boundary + "\r\n");
		write(out, "Content-Disposition: form-data; name=\"reqtype\"\r\n\r\n");
		write(out, "fileupload\r\n");
		write(out, "--" + boundary + "\r\n");
		write(out, "Content-Disposition: form-data; name=\"fileToUpload\"; filename=\"image.png\"\r\n");
		write(out, "Content-Type: image/png\r\n\r\n");
		out.write(data, 0, data.length);
		write(out, "\r\n--" + boundary + "--\r\n");
		byte[] body = out.toByteArray();
		String apiUrl = config.imageUploaderUrl != null && !config.imageUploaderUrl.isBlank()
				? config.imageUploaderUrl : "https://catbox.moe/user/api.php";
		return postRaw(apiUrl, body, boundary, config, client);
	}

	private static String uploadTo0x0(String imageUrl, ChatConfig config, HttpClient client) {
		byte[] data = download(imageUrl, config, client);
		if (data == null) {
			return null;
		}
		String boundary = "----AiChatMod0x0" + UUID.randomUUID();
		byte[] body = multipart("file", boundary, data).getBytes(StandardCharsets.ISO_8859_1);
		String apiUrl = config.imageUploaderUrl != null && !config.imageUploaderUrl.isBlank()
				? config.imageUploaderUrl : "https://0x0.st";
		return postRaw(apiUrl, body, boundary, config, client);
	}

	private static String uploadToImgbb(String imageUrl, ChatConfig config, HttpClient client) {
		if (config.imageUploaderUrl == null || config.imageUploaderUrl.isBlank()) {
			System.err.println("[mcai] imgbb 上传需要在 imageUploaderUrl 中提供 API 地址。");
			return null;
		}
		byte[] data = download(imageUrl, config, client);
		if (data == null) {
			return null;
		}
		String boundary = "----AiChatModImgbb" + UUID.randomUUID();
		byte[] body = multipart("image", boundary, data).getBytes(StandardCharsets.ISO_8859_1);
		String resp = postRaw(config.imageUploaderUrl, body, boundary, config, client);
		if (resp == null) {
			return null;
		}
		return extractUrlFromResponse(resp);
	}

	private static String uploadToCustomHost(String imageUrl, ChatConfig config, HttpClient client) {
		byte[] data = download(imageUrl, config, client);
		if (data == null) {
			return null;
		}
		String boundary = "----AiChatModCustom" + UUID.randomUUID();
		byte[] body = multipart("file", boundary, data).getBytes(StandardCharsets.ISO_8859_1);
		String resp = postRaw(config.imageUploaderUrl, body, boundary, config, client);
		if (resp == null) {
			return null;
		}
		return extractUrlFromResponse(resp);
	}

	private static String postRaw(String url, byte[] body, String boundary, ChatConfig config, HttpClient client) {
		try {
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(url))
					.timeout(Duration.ofSeconds(config.imageTimeoutSeconds))
					.header("Content-Type", "multipart/form-data; boundary=" + boundary)
					.header("User-Agent", USER_AGENT)
					.version(HttpClient.Version.HTTP_1_1)
					.POST(HttpRequest.BodyPublishers.ofByteArray(body))
					.build();
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() >= 200 && response.statusCode() < 300) {
				return response.body().trim();
			}
			System.err.println("[mcai] Upload failed: HTTP " + response.statusCode() + " " + response.body());
			return null;
		} catch (Exception e) {
			System.err.println("[mcai] Upload error: " + e);
			return null;
		}
	}

	private static String extractUrlFromResponse(String responseBody) {
		if (responseBody == null) {
			return null;
		}
		String trimmed = responseBody.trim();
		if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
			return trimmed;
		}
		try {
			JsonObject obj = JsonParser.parseString(responseBody).getAsJsonObject();
			if (obj.has("url") && !obj.get("url").isJsonNull()) {
				return obj.get("url").getAsString();
			}
			if (obj.has("data") && obj.get("data").isJsonObject()) {
				JsonObject data = obj.getAsJsonObject("data");
				if (data.has("url") && !data.get("url").isJsonNull()) {
					return data.get("url").getAsString();
				}
				if (data.has("link") && !data.get("link").isJsonNull()) {
					return data.get("link").getAsString();
				}
				if (data.has("image") && data.get("image").isJsonObject()
						&& data.getAsJsonObject("image").has("url")
						&& !data.getAsJsonObject("image").get("url").isJsonNull()) {
					return data.getAsJsonObject("image").get("url").getAsString();
				}
			}
		} catch (Exception ignored) {
		}
		return null;
	}
}
