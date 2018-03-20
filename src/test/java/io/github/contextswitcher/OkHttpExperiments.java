package io.github.contextswitcher;

import java.io.IOException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.Test;

public class OkHttpExperiments {

    OkHttpClient client = new OkHttpClient();

    String run(String url) throws IOException {
        Request request = new Request.Builder()
            .url(url)
            .build();

        try (Response response = client.newCall(request).execute()) {
            return response.body().string();
        }
    }

    @Test
    public void runOkHttp() throws IOException {
        String response = this.run("https://raw.github.com/square/okhttp/master/README.md");
        System.out.println(response);
    }

}
